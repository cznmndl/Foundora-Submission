package com.foundora.service;

import com.foundora.model.ClaimRecord;
import com.foundora.model.UserProfile;

/**
 * What only admins see about a post: where it was lost or found, its identifying details, who reported it,
 * and its current claim.
 * Built only for admin pages ({@link AdminDetailsService}); non-admin pages never receive it.
 *
 * @param privateDetails  identifying marks the reporter kept off the public description; null if none
 * @param reporterProfile null if the reporter has no profile yet (e.g. reported before profiles existed)
 * @param claim           the current (not reopened) claim record, or null
 * @param money           amount, denominations, container and time for a money report; null otherwise
 */
public record AdminPostDetails(String location, String locationSpot, String privateDetails, String reporterEmail,
                               UserProfile reporterProfile, ClaimRecord claim, MoneyDetails money) {
}
