package com.event_ticket_booking.backend.service;

import com.event_ticket_booking.backend.dto.request.BookingCreateRequest;
import com.event_ticket_booking.backend.dto.request.BookingItemRequest;
import com.event_ticket_booking.backend.dto.response.BookingResponse;
import com.event_ticket_booking.backend.entity.*;
import com.event_ticket_booking.backend.exception.BusinessException;
import com.event_ticket_booking.backend.exception.ResourceNotFoundException;
import com.event_ticket_booking.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final ConcertRepository concertRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final VoucherRepository voucherRepository;
    private final VoucherRedemptionRepository voucherRedemptionRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponse createBooking(Long userId, BookingCreateRequest request) {
        // [4.2] Idempotency Check
        java.util.Optional<Booking> existingBooking = bookingRepository
                .findByIdempotencyKey(request.getIdempotencyKey());
        if (existingBooking.isPresent()) {
            return mapToResponse(existingBooking.get());
        }

        // [4.1] Input Validation
        Concert concert = concertRepository.findById(request.getConcertId())
                .orElseThrow(() -> new ResourceNotFoundException("Concert not found"));

        if (concert.getStatus() != Concert.Status.PUBLISHED) {
            throw new BusinessException("Concert is not published");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(concert.getSaleStartAt()) || now.isAfter(concert.getSaleEndAt())) {
            throw new BusinessException("Concert is not within sale period");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // [4.3] Atomic Reserve
        // Sort items by ticketCategoryId ascending to avoid deadlocks (AGENTS.md §2.3)
        List<BookingItemRequest> sortedItems = new ArrayList<>(request.getItems());
        sortedItems.sort(Comparator.comparing(BookingItemRequest::getTicketCategoryId));

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<BookingItem> bookingItems = new ArrayList<>();

        for (BookingItemRequest itemReq : sortedItems) {
            TicketCategory category = ticketCategoryRepository.findById(itemReq.getTicketCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ticket category not found"));

            if (!category.getConcert().getId().equals(concert.getId())) {
                throw new BusinessException("Ticket category does not belong to this concert");
            }

            int affectedRows = ticketCategoryRepository.reserveTickets(category.getId(), itemReq.getQuantity());
            if (affectedRows == 0) {
                throw new BusinessException("InsufficientTicketException: Ticket category " + category.getName()
                        + " sold out or not enough quantity");
            }

            BookingItem item = new BookingItem();
            item.setTicketCategory(category);
            item.setQuantity(itemReq.getQuantity());
            item.setUnitPrice(category.getPrice()); // Snapshot price

            BigDecimal subtotal = category.getPrice().multiply(new BigDecimal(itemReq.getQuantity()));
            item.setSubtotal(subtotal);

            totalAmount = totalAmount.add(subtotal);
            bookingItems.add(item);
        }

        // [4.4] Voucher Application
        Voucher appliedVoucher = null;
        BigDecimal discountAmount = BigDecimal.ZERO;

        if (request.getVoucherCode() != null && !request.getVoucherCode().trim().isEmpty()) {
            appliedVoucher = voucherRepository.findByCodeForUpdate(request.getVoucherCode())
                    .orElseThrow(() -> new BusinessException("Voucher not found"));

            if (now.isBefore(appliedVoucher.getValidFrom()) || now.isAfter(appliedVoucher.getValidUntil())) {
                throw new BusinessException("Voucher is not valid at this time");
            }

            if (appliedVoucher.getConcert() != null && !appliedVoucher.getConcert().getId().equals(concert.getId())) {
                throw new BusinessException("Voucher is not applicable for this concert");
            }

            if (appliedVoucher.getUsedCount() >= appliedVoucher.getMaxUsage()) {
                throw new BusinessException("Voucher quota exceeded");
            }

            long userUsage = voucherRedemptionRepository.countAppliedByVoucherAndUser(appliedVoucher.getId(), userId);
            if (userUsage >= appliedVoucher.getMaxUsagePerUser()) {
                throw new BusinessException("User has exceeded voucher usage limit");
            }

            // Calculate discount
            if (appliedVoucher.getDiscountType() == Voucher.DiscountType.PERCENTAGE) {
                // e.g. 10.00%
                discountAmount = totalAmount.multiply(appliedVoucher.getDiscountValue())
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            } else if (appliedVoucher.getDiscountType() == Voucher.DiscountType.FIXED_AMOUNT) {
                discountAmount = appliedVoucher.getDiscountValue();
            }

            // Prevent negative total
            if (discountAmount.compareTo(totalAmount) > 0) {
                discountAmount = totalAmount;
            }
        }

        // [4.5] Save Booking and Items
        String generatedCode = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Booking booking = new Booking();
        booking.setReservedAt(LocalDateTime.now());
        booking.setBookingCode(generatedCode);
        booking.setUser(user);
        booking.setConcert(concert);
        booking.setVoucher(appliedVoucher);
        booking.setStatus(Booking.Status.PENDING);
        booking.setTotalAmount(totalAmount);
        booking.setDiscountAmount(discountAmount);
        booking.setIdempotencyKey(request.getIdempotencyKey());
        booking.setExpiresAt(now.plusMinutes(10));

        Booking savedBooking = bookingRepository.save(booking);

        for (BookingItem item : bookingItems) {
            item.setBooking(savedBooking);
            bookingItemRepository.save(item);
        }

        if (appliedVoucher != null) {
            voucherRepository.incrementUsedCount(appliedVoucher.getId());

            VoucherRedemption redemption = new VoucherRedemption();
            redemption.setVoucher(appliedVoucher);
            redemption.setUser(user);
            redemption.setBooking(savedBooking);
            redemption.setStatus(VoucherRedemption.Status.APPLIED);
            voucherRedemptionRepository.save(redemption);
        }

        return mapToResponse(savedBooking);
    }

    private BookingResponse mapToResponse(Booking booking) {
        return BookingResponse.builder()
                .id(booking.getId())
                .bookingCode(booking.getBookingCode())
                .status(booking.getStatus())
                .totalAmount(booking.getTotalAmount())
                .discountAmount(booking.getDiscountAmount())
                .idempotencyKey(booking.getIdempotencyKey())
                .expiresAt(booking.getExpiresAt())
                .createdAt(booking.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getBookingByCode(String bookingCode) {
        Booking booking = bookingRepository.findByBookingCode(bookingCode)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        return mapToResponse(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BookingResponse> getUserBookings(Long userId, Pageable pageable) {
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::mapToResponse);
    }
}
