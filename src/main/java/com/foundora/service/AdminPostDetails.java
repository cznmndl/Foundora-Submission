package com.foundora.service;

import com.foundora.model.ClaimRecord;
import com.foundora.model.UserProfile;

/**
 * What only admins see about a post: where it was lost or found, who reported it, and its current claim.
 * Built only for admin pages ({@link AdminDetailsService}); non-admin pages never receive it.
 *
 * @param reporterProfile null if the reporter has no profile yet (e.g. reported before profiles existed)
 * @param claim           the current (not reopened) claim record, or null
 */
public record AdminPostDetails(String location, String locationSpot, String reporterEmail,
                               UserProfile reporterProfile, ClaimRecord claim) {
}
