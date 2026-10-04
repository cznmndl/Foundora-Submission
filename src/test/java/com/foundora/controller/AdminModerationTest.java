package com.foundora.controller;

import com.foundora.auth.FoundoraUser;
import com.foundora.auth.UserRole;
import com.foundora.model.Item;
import com.foundora.repository.ItemRepository;
import com.foundora.service.ItemService;
import com.google.firebase.auth.FirebaseAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminModerationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ItemService itemService;

    @Autowired
    private ItemRepository itemRepository;

    @MockitoBean
    private FirebaseAuth firebaseAuth;

    @BeforeEach
    void clearItems() {
        itemRepository.deleteAll();
    }

    @Test
    void approvedItemMovesFromAdminQueueToMarketplace() throws Exception {
        Item item = itemService.addItem(newItem("Blue Umbrella"));

        mvc.perform(get("/admin").with(as(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Blue Umbrella")));
        mvc.perform(get("/").with(as(UserRole.STUDENT)))
                .andExpect(content().string(not(containsString("Blue Umbrella"))));

        mvc.perform(post("/admin/approve/" + item.getId()).with(as(UserRole.ADMIN)).with(csrf()))
                .andExpect(redirectedUrl("/admin"));

        assertThat(itemRepository.findById(item.getId())).get()
                .extracting(Item::getApprovalStatus).isEqualTo("APPROVED");
        mvc.perform(get("/admin").with(as(UserRole.ADMIN)))
                .andExpect(content().string(not(containsString("Blue Umbrella"))));
        mvc.perform(get("/").with(as(UserRole.STUDENT)))
                .andExpect(content().string(containsString("Blue Umbrella")));
    }

    @Test
    void rejectedItemLeavesQueueAndNeverReachesMarketplace() throws Exception {
        Item item = itemService.addItem(newItem("Fake Report"));

        mvc.perform(post("/admin/reject/" + item.getId()).with(as(UserRole.ADMIN)).with(csrf()))
                .andExpect(redirectedUrl("/admin"));

        assertThat(itemRepository.findById(item.getId())).get()
                .extracting(Item::getApprovalStatus).isEqualTo("REJECTED");
        mvc.perform(get("/admin").with(as(UserRole.ADMIN)))
                .andExpect(content().string(not(containsString("Fake Report"))));
        mvc.perform(get("/").with(as(UserRole.STUDENT)))
                .andExpect(content().string(not(containsString("Fake Report"))));
    }

    @Test
    void moderationRequiresAdminAndCsrfAndAnExistingItem() throws Exception {
        Item item = itemService.addItem(newItem("Calculator"));

        mvc.perform(post("/admin/approve/" + item.getId()).with(as(UserRole.STUDENT)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/approve/" + item.getId()).with(as(UserRole.ADMIN)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/approve/" + UUID.randomUUID()).with(as(UserRole.ADMIN)).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(itemRepository.findById(item.getId())).get()
                .extracting(Item::getApprovalStatus).isEqualTo("PENDING");
    }

    private static RequestPostProcessor as(UserRole role) {
        String email = role == UserRole.ADMIN ? "ana.cruz@dlsl.edu.ph" : "juan_delacruz@dlsl.edu.ph";
        FoundoraUser user = new FoundoraUser("uid-" + role, email, "Test User", role);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                user, null, List.of(new SimpleGrantedAuthority(role.authority()))));
    }

    private static Item newItem(String title) {
        Item item = new Item();
        item.setTitle(title);
        item.setDescription("Left near the canteen");
        item.setCategory("Umbrellas");
        item.setLocation("Canteen");
        item.setDateLostFound(LocalDate.of(2026, 10, 1));
        item.setStatus("FOUND");
        item.setReporterName("Juan Dela Cruz");
        item.setReporterEmail("juan_delacruz@dlsl.edu.ph");
        return item;
    }
}
