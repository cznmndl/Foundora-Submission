package com.foundora.moderation;

import com.foundora.database.entity.ClaimRecord;
import com.foundora.database.entity.UserProfile;

public record AdminPostDetails(String location, String locationSpot, String privateDetails, String reporterEmail,
                               UserProfile reporterProfile, ClaimRecord claim, MoneyDetails money) {
}
