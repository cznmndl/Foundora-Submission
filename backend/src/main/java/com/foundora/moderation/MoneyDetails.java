package com.foundora.moderation;

import com.foundora.database.entity.ClaimRequest;
import com.foundora.database.entity.Item;
import com.foundora.database.entity.MoneyContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record MoneyDetails(BigDecimal amount, String denominations, String where, LocalDate date, LocalTime time,
                           MoneyContainer container) {

    public static MoneyDetails of(Item item) {
        if (!item.isMoney() || item.getMoneyAmount() == null) {
            return null;
        }
        String where = item.getLocationSpot() == null ? item.getLocation()
                : item.getLocation() + " · " + item.getLocationSpot();
        return new MoneyDetails(item.getMoneyAmount(), item.getMoneyDenominations(), where,
                item.getDateLostFound(), item.getTimeLostFound(), item.getMoneyContainer());
    }

    public static MoneyDetails of(ClaimRequest request) {
        if (!request.isMoney()) {
            return null;
        }
        return new MoneyDetails(request.getMoneyAmount(), request.getMoneyDenominations(), request.getMoneyWhere(),
                request.getMoneyLostOn(), request.getMoneyLostTime(), request.getMoneyContainer());
    }
}
