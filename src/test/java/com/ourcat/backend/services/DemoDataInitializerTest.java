package com.ourcat.backend.services;

import com.ourcat.backend.models.User;
import com.ourcat.backend.repositories.CatRepository;
import com.ourcat.backend.repositories.OrganizationRepository;
import com.ourcat.backend.repositories.PostRepository;
import com.ourcat.backend.repositories.RescueActivityRepository;
import com.ourcat.backend.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("demo")
@TestPropertySource(properties = {
        "ourcat.demo.data-dir=./target/demo-profile-test",
        "OURCAT_DEMO_JWT_SECRET=integration-test-demo-jwt-secret-with-more-than-32-bytes",
        "OURCAT_DEMO_ADMIN_USERNAME=demo-admin",
        "OURCAT_DEMO_ADMIN_PASSWORD=demo-admin-test-password"
})
class DemoDataInitializerTest {

    @Autowired
    private DemoDataInitializer initializer;

    @Autowired
    private CatRepository catRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private RescueActivityRepository rescueActivityRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void seedsUsableContentAndCanRunAgainWithoutDuplicatingIt() throws Exception {
        assertEquals(2, catRepository.count());
        assertEquals(1, organizationRepository.count());
        assertEquals(2, postRepository.count());
        assertEquals(1, rescueActivityRepository.count());
        assertEquals(2, userRepository.count());
        assertEquals(3, userRepository.findByUsername("demo-admin").orElseThrow().getRole());

        User admin = userRepository.findByUsername("demo-admin").orElseThrow();
        String originalPasswordHash = admin.getPassword();
        initializer.run(new DefaultApplicationArguments(new String[0]));

        assertEquals(2, catRepository.count());
        assertEquals(1, organizationRepository.count());
        assertEquals(2, postRepository.count());
        assertEquals(1, rescueActivityRepository.count());
        assertEquals(originalPasswordHash,
                userRepository.findByUsername("demo-admin").orElseThrow().getPassword());
        assertNotNull(organizationRepository.findById(1L).orElse(null));
        assertTrue(catRepository.findAll().stream().allMatch(cat -> cat.getName() != null));
    }
}
