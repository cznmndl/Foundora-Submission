package com.foundora.support;

import com.foundora.auth.FoundoraUser;
import com.foundora.auth.UserRole;
import com.foundora.database.entity.Category;
import com.foundora.database.entity.Item;
import com.foundora.database.entity.ReportStatus;
import com.foundora.database.entity.ReportType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

public final class TestData {

    public static final FoundoraUser STUDENT =
            new FoundoraUser("uid-juan", "juan_delacruz@dlsl.edu.ph", "Juan Dela Cruz", UserRole.STUDENT);
    public static final FoundoraUser OTHER_STUDENT =
            new FoundoraUser("uid-maria", "maria_santos@dlsl.edu.ph", "Maria Santos", UserRole.STUDENT);
    public static final FoundoraUser STAFF =
            new FoundoraUser("uid-ben", "ben.reyes@dlsl.edu.ph", "Ben Reyes", UserRole.STAFF);
    public static final FoundoraUser ADMIN =
            new FoundoraUser("uid-ana", "ana.cruz@dlsl.edu.ph", "Ana Cruz", UserRole.ADMIN);

    private TestData() {
    }

    public static RequestPostProcessor as(FoundoraUser user) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                user, null, List.of(new SimpleGrantedAuthority(user.role().authority()))));
    }

    public static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Manila"));
    }

    public static Item item(String name, ReportType type, ReportStatus status, FoundoraUser reporter) {
        return item(name, type, status, reporter, Instant.now());
    }

    public static Item item(String name, ReportType type, ReportStatus status, FoundoraUser reporter, Instant createdAt) {
        Item item = new Item();
        item.setItemName(name);
        item.setDescription("Left near the canteen benches");
        item.setReportType(type);
        item.setCategory(Category.UMBRELLAS);
        item.setLocation("Sentrum");
        item.setLocationSpot("Bench beside the vending machine");
        item.setDateLostFound(today().minusDays(1));
        item.setStatus(status);
        item.setReporterUid(reporter.uid());
        item.setReporterName(reporter.name());
        item.setReporterEmail(reporter.email());
        item.setCreatedAt(createdAt);
        item.setUpdatedAt(createdAt);
        if (status == ReportStatus.REJECTED) {
            item.setReviewReason("Duplicate report");
        }
        return item;
    }

    public static Instant minutesAgo(long minutes) {
        return Instant.now().minus(Duration.ofMinutes(minutes));
    }

    /** A blank PNG of the given size, for upload tests. */
    public static byte[] png(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }
}
