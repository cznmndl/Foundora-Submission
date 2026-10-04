package com.foundora.support;

import com.foundora.feed.FeedIndex;
import com.foundora.model.Item;
import com.foundora.repository.ActivityLogRepository;
import com.foundora.repository.ItemRepository;
import com.foundora.service.StorageService;
import com.google.firebase.auth.FirebaseAuth;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Full app on the in-memory database, with Firebase and Supabase Storage replaced by mocks. */
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
    protected FeedIndex feedIndex;

    @MockitoBean
    protected FirebaseAuth firebaseAuth;

    @MockitoBean
    protected StorageService storage;

    @BeforeEach
    void clearData() {
        activityLog.deleteAll();
        items.deleteAll();
        feedIndex.rebuild();
    }

    /** Saves items directly (as if already moderated) and reloads the feed index. */
    protected Item save(Item item) {
        Item saved = items.save(item);
        feedIndex.rebuild();
        return saved;
    }
}
