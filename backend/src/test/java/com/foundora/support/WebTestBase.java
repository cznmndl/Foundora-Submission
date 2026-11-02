package com.foundora.support;

import com.foundora.auth.FoundoraUser;
import com.foundora.database.entity.Item;
import com.foundora.database.entity.UserProfile;
import com.foundora.database.repository.ActivityLogRepository;
import com.foundora.database.repository.ClaimRecordRepository;
import com.foundora.database.repository.ClaimRequestRepository;
import com.foundora.database.repository.CommentRepository;
import com.foundora.database.repository.ItemRepository;
import com.foundora.database.repository.LoginAttemptRepository;
import com.foundora.database.repository.UserProfileRepository;
import com.foundora.database.storage.StorageService;
import com.foundora.feed.FeedIndex;
import com.google.firebase.auth.FirebaseAuth;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class WebTestBase {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ItemRepository items;

    @Autowired
    protected ActivityLogRepository activityLog;

    @Autowired
    protected CommentRepository commentRepository;

    @Autowired
    protected ClaimRecordRepository claimRepository;

    @Autowired
    protected ClaimRequestRepository claimRequestRepository;

    @Autowired
    protected UserProfileRepository profileRepository;

    @Autowired
    protected LoginAttemptRepository loginAttempts;

    @Autowired
    protected FeedIndex feedIndex;

    @MockitoBean
    protected FirebaseAuth firebaseAuth;

    @MockitoBean
    protected StorageService storage;

    @BeforeEach
    void clearData() {
        activityLog.deleteAll();
        loginAttempts.deleteAll();
        commentRepository.deleteAll();
        claimRepository.deleteAll();
        claimRequestRepository.deleteAll();
        items.deleteAll();
        profileRepository.deleteAll();
        profileRepository.save(profile(TestData.STUDENT, "BS Information Technology", 2, "O1A", null));
        profileRepository.save(profile(TestData.OTHER_STUDENT, "BS Psychology", 3, "P3B", null));
        profileRepository.save(profile(TestData.STAFF, null, null, null, "Registrar's Office"));
        profileRepository.save(profile(TestData.ADMIN, null, null, null, "SDFO"));
        feedIndex.rebuild();
    }

    protected static UserProfile profile(FoundoraUser user, String course, Integer year, String section,
                                         String department) {
        UserProfile p = new UserProfile(user.uid(), user.email(), user.name(), user.role(), Instant.now());
        p.setCourse(course);
        p.setYearLevel(year);
        p.setSection(section);
        p.setDepartment(department);
        return p;
    }

    protected String html(String url, RequestPostProcessor user) throws Exception {
        return mvc.perform(get(url).with(user)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    protected Item save(Item item) {
        Item saved = items.save(item);
        feedIndex.rebuild();
        return saved;
    }
}
