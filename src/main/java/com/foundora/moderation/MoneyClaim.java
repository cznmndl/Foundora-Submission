package com.foundora.moderation;


import com.foundora.database.entity.ClaimRequest;
import com.foundora.database.entity.Item;
import com.foundora.database.entity.MoneyContainer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/** A claimant's checked description of the money they lost. */
record MoneyClaim(BigDecimal amount, String denominations, String where, LocalDate lostOn, LocalTime lostTime,
                  MoneyContainer container) {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

    /** Throws {@link ClaimRequestException} with a message for the person if something is missing or wrong. */
    static MoneyClaim parse(MoneyClaimInput in, Clock clock) {
        BigDecimal amount = amount(in.amount());
        String denominations = blankToNull(in.denominations());
        if (denominations != null && denominations.length() > Item.MAX_DENOMINATIONS) {
            throw new ClaimRequestException("Keep the denominations to " + Item.MAX_DENOMINATIONS
                    + " characters or fewer.");
        }
        String where = blankToNull(in.where());
        if (where == null) {
            throw new ClaimRequestException("Say where you lost the money.");
        }
        if (where.length() > ClaimRequest.MAX_MONEY_WHERE) {
            throw new ClaimRequestException("Keep where you lost it to " + ClaimRequest.MAX_MONEY_WHERE
                    + " characters or fewer.");
        }
        LocalDate lostOn;
        try {
            lostOn = LocalDate.parse(blankToEmpty(in.lostOn()));
        } catch (DateTimeParseException e) {
            throw new ClaimRequestException("Enter the date you lost the money.");
        }
        if (lostOn.isAfter(LocalDate.now(clock))) {
            throw new ClaimRequestException("The date you lost it can't be in the future.");
        }
        LocalTime lostTime = null;
        if (blankToNull(in.lostTime()) != null) {
            try {
                lostTime = LocalTime.parse(in.lostTime().strip());
            } catch (DateTimeParseException e) {
                throw new ClaimRequestException("Enter the time like 13:30, or leave it blank.");
            }
        }
        MoneyContainer container;
        try {
            container = MoneyContainer.valueOf(blankToEmpty(in.container()));
        } catch (IllegalArgumentException e) {
            throw new ClaimRequestException("Choose what the money was in.");
        }
        return new MoneyClaim(amount, denominations, where, lostOn, lostTime, container);
    }

    private static BigDecimal amount(String text) {
        String cleaned = blankToEmpty(text).replace(",", "").replace("₱", "").strip();
        if (cleaned.isEmpty()) {
            throw new ClaimRequestException("Enter how much money you lost.");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            throw new ClaimRequestException("Enter the amount as a number, e.g. 500 or 1250.50.");
        }
        if (amount.signum() <= 0 || amount.scale() > 2 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new ClaimRequestException("Enter the amount in pesos, e.g. 500 or 1250.50.");
        }
        return amount;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.strip();
    }
}
