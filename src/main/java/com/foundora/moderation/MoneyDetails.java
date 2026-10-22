package com.foundora.moderation;


import com.foundora.database.entity.ClaimRequest;
import com.foundora.database.entity.Item;
import com.foundora.database.entity.MoneyContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * The private description of some money: the finder's or loser's report, or what a claimant says. Shown only to
 * admins (and to the person who wrote it), so SDFO can compare the two side by side.
 *
 * @param denominations null if not given
 * @param time          null if not given
 */
public record MoneyDetails(BigDecimal amount, String denominations, String where, LocalDate date, LocalTime time,
                           MoneyContainer container) {

    /** The report's details, or null if it isn't a money report. */
    public static MoneyDetails of(Item item) {
        if (!item.isMoney() || item.getMoneyAmount() == null) {
            return null;
        }
        String where = item.getLocationSpot() == null ? item.getLocation()
                : item.getLocation() + " · " + item.getLocationSpot();
        return new MoneyDetails(item.getMoneyAmount(), item.getMoneyDenominations(), where,
                item.getDateLostFound(), item.getTimeLostFound(), item.getMoneyContainer());
    }

    /** The claimant's description, or null if the request isn't about money. */
    public static MoneyDetails of(ClaimRequest request) {
        if (!request.isMoney()) {
            return null;
        }
        return new MoneyDetails(request.getMoneyAmount(), request.getMoneyDenominations(), request.getMoneyWhere(),
                request.getMoneyLostOn(), request.getMoneyLostTime(), request.getMoneyContainer());
    }

    public boolean sameAmount(MoneyDetails other) {
        return other != null && amount.compareTo(other.amount) == 0;
    }

    public boolean sameContainer(MoneyDetails other) {
        return other != null && container == other.container;
    }
}
