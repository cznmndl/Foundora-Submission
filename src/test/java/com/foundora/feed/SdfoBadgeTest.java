package com.foundora.feed;

import com.foundora.database.entity.Item;
import com.foundora.database.entity.ReportStatus;
import com.foundora.database.entity.ReportType;
import com.foundora.support.WebTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static com.foundora.support.TestData.ADMIN;
import static com.foundora.support.TestData.STUDENT;
import static com.foundora.support.TestData.as;
import static com.foundora.support.TestData.item;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** Comments from accounts on foundora.sdfo.emails carry the "SDFO" badge instead of "Admin". */
@TestPropertySource(properties = "foundora.sdfo.emails=ana.cruz@dlsl.edu.ph")
class SdfoBadgeTest extends WebTestBase {

    @Test
    void sdfoAccountsGetTheSdfoBadge() throws Exception {
        Item post = save(item("Navy umbrella", ReportType.FOUND, ReportStatus.APPROVED, STUDENT));
        mvc.perform(multipart("/posts/" + post.getId() + "/comments").param("body", "Please drop by the office.")
                .with(as(ADMIN)).with(csrf()));

        String html = mvc.perform(get("/posts/" + post.getId()).with(as(STUDENT)))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Please drop by the office.").contains(">SDFO<");
    }
}
