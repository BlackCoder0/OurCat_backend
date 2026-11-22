package com.ourcat.backend.services;

import com.ourcat.backend.models.Cat;
import com.ourcat.backend.models.Organization;
import com.ourcat.backend.models.OrganizationMember;
import com.ourcat.backend.models.Post;
import com.ourcat.backend.models.RescueActivity;
import com.ourcat.backend.models.User;
import com.ourcat.backend.repositories.CatRepository;
import com.ourcat.backend.repositories.OrganizationMemberRepository;
import com.ourcat.backend.repositories.OrganizationRepository;
import com.ourcat.backend.repositories.PostRepository;
import com.ourcat.backend.repositories.RescueActivityRepository;
import com.ourcat.backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoDataInitializer implements ApplicationRunner {

    private static final String SAMPLE_USERNAME = "ourcat-demo-author";

    private final CatRepository catRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final PostRepository postRepository;
    private final RescueActivityRepository rescueActivityRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ourcat.demo.admin.username:}")
    private String adminUsername;

    @Value("${ourcat.demo.admin.password:}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        Organization organization = ensureOrganization();
        User author = ensureSampleAuthor();
        List<Cat> cats = ensureSampleCats();
        seedPosts(author, cats);
        seedRescueActivity(organization, author, cats.get(0));
        seedAdmin(organization);
    }

    private Organization ensureOrganization() {
        return organizationRepository.findById(1L).orElseGet(() -> {
            if (organizationRepository.count() != 0) {
                throw new IllegalStateException("Demo database is missing the default organization");
            }
            return organizationRepository.save(Organization.builder()
                    .name("本地演示救助组织")
                    .description("仅保存在本地体验数据库中的示例组织")
                    .build());
        });
    }

    private User ensureSampleAuthor() {
        return userRepository.findByUsername(SAMPLE_USERNAME).orElseGet(() -> userRepository.save(User.builder()
                .username(SAMPLE_USERNAME)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .nickname("本地演示账号")
                .role(1)
                .build()));
    }

    private List<Cat> ensureSampleCats() {
        if (catRepository.count() == 0) {
            catRepository.saveAll(List.of(
                    Cat.builder().name("小橘").color("橘色").feature("短毛、鼻尖有浅色斑点")
                            .personality("亲人").status("活跃").build(),
                    Cat.builder().name("灰灰").color("灰白").feature("中长毛、胸口白色")
                            .personality("胆小").status("活跃").build()));
        }
        return catRepository.findAll();
    }

    private void seedPosts(User author, List<Cat> cats) {
        if (postRepository.count() == 0) {
            postRepository.saveAll(List.of(
                    Post.builder().title("本地演示：校园猫咪观察记录")
                            .content("这里的内容保存在你的本地数据库中，可以注册账号后发布和评论。")
                            .userId(author.getId()).referencedCatId(cats.get(0).getId()).build(),
                    Post.builder().title("本地演示：分享救助经验")
                            .content("AI 识猫默认关闭；本地图片上传尚未配置。")
                            .userId(author.getId()).referencedCatId(cats.get(1).getId()).build()));
        }
    }

    private void seedRescueActivity(Organization organization, User author, Cat cat) {
        if (rescueActivityRepository.count() == 0) {
            rescueActivityRepository.save(RescueActivity.builder()
                    .title("本地演示：查看这条救助活动")
                    .description("这是用于浏览和研究的本地示例，不代表真实救助任务。")
                    .catId(cat.getId())
                    .organizationId(organization.getId())
                    .problemType("其他")
                    .urgency("normal")
                    .status("created")
                    .createdBy(author.getId())
                    .build());
        }
    }

    private void seedAdmin(Organization organization) {
        if (adminUsername.isBlank()) {
            return;
        }
        User admin = userRepository.findByUsername(adminUsername).orElseGet(() -> userRepository.save(User.builder()
                .username(adminUsername)
                .password(passwordEncoder.encode(adminPassword))
                .nickname("本地体验管理员")
                .role(3)
                .build()));
        if (admin.getRole() != 3) {
            throw new IllegalStateException("Configured demo admin already exists without the admin role");
        }
        if (!organizationMemberRepository.existsByOrganizationIdAndUserId(organization.getId(), admin.getId())) {
            organizationMemberRepository.save(OrganizationMember.builder()
                    .organizationId(organization.getId())
                    .userId(admin.getId())
                    .role("admin")
                    .build());
        }
    }
}
