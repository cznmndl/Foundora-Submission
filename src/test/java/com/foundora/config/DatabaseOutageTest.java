package com.foundora.config;

import com.foundora.database.entity.Item;
import com.foundora.database.entity.ReportStatus;
import com.foundora.database.entity.ReportType;
import com.foundora.database.repository.ItemRepository;
import com.foundora.moderation.NavCounts;
import com.foundora.support.WebTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.CannotCreateTransactionException;

import static com.foundora.support.TestData.ADMIN;
import static com.foundora.support.TestData.OTHER_STUDENT;
import static com.foundora.support.TestData.STUDENT;
import static com.foundora.support.TestData.as;
import static com.foundora.support.TestData.item;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** What people see while Supabase's database can't be reached. */
class DatabaseOutageTest extends WebTestBase {

    @MockitoSpyBean
    private ItemRepository itemRepository;

    @Autowired
    private NavCounts navCounts;

    @Test
    void peopleSeeATemporaryErrorPageNotAStackTrace() throws Exception {
        doThrow(new DataAccessResourceFailureException("Connection to db.supabase.co refused"))
                .when(itemRepository).findByReporterEmailOrderByCreatedAtDesc(anyString());

        mvc.perform(get("/my-reports").with(as(STUDENT)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(containsString("Foundora can't reach its database right now")))
                .andExpect(content().string(not(containsString("db.supabase.co"))));
    }

    @Test
    void theOutagePageStillRendersForAdmins() throws Exception {
        doThrow(new CannotCreateTransactionException("Could not open JPA EntityManager for transaction"))
                .when(itemRepository).countByStatus(any());

        mvc.perform(get("/admin").with(as(ADMIN)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(containsString("Foundora can't reach its database right now")));
        // Any error page with the admin sidebar shows its counts as 0 rather than failing to render.
        assertThat(navCounts.pending()).isZero();
    }

    @Test
    void theFeedKeepsShowingItsLastPostsWhileTheDatabaseIsDown() throws Exception {
        Item post = save(item("Navy umbrella", ReportType.FOUND, ReportStatus.APPROVED, OTHER_STUDENT));
        doThrow(new DataAccessResourceFailureException("Connection refused"))
                .when(itemRepository).findByStatusIn(any());

        feedIndex.rebuild(); // fails, so the index keeps what it had

        assertThat(html("/", as(STUDENT))).contains("Navy umbrella").contains("/posts/" + post.getId());
    }
}
