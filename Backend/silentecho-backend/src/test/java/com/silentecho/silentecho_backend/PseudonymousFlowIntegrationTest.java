package com.silentecho.silentecho_backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end checks of the pseudonymous flow through the real Spring Security filter chain.
 *
 * <p>MockMvc is built programmatically with {@code springSecurity()} rather than with
 * {@code @AutoConfigureMockMvc}. That annotation moved packages in Spring Boot 4, and this avoids
 * depending on the package name. Each test uses its own client address so the login throttle
 * cannot leak between tests. Requests carry their address and session through a single
 * {@link RequestPostProcessor}, which works for both plain and multipart request builders.
 */
@SpringBootTest
@ActiveProfiles("test")
class PseudonymousFlowIntegrationTest {

    private static final Pattern SIGN_IN_ID = Pattern.compile("\"signInId\":\"(SE-[A-Za-z0-9_-]{43})\"");
    private static final Pattern CASE_ID = Pattern.compile("\"caseId\":\"([0-9a-f-]{36})\"");
    private static final Pattern FILE_ID = Pattern.compile("\"fileId\":\"([0-9a-f-]{36})\"");
    private static final Pattern ORG_ID = Pattern.compile("\"organisations\":\\[\\{\"id\":\"([0-9a-f-]{36})\"");
    private static final Pattern ORG_CODE = Pattern.compile("\"organisationCode\":\"(SE-ORG-[A-Z2-9]{8})\"");
    private static final Pattern RECOVERY_CODE = Pattern.compile("\"organisationRecoveryCode\":\"(SER-[A-Za-z0-9_-]{43})\"");

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /** A registered account together with its server-side session. */
    private record Session(MockHttpSession http, String address, String signInId) {
    }

    /** A registered organisation together with its administrator's session. */
    private record Organisation(String id, Session admin, String organisationCode, String recoveryCode) {
    }

    private static RequestPostProcessor as(String address, MockHttpSession session) {
        return request -> {
            request.setRemoteAddr(address);
            if (session != null) {
                request.setSession(session);
            }
            return request;
        };
    }

    private static String first(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        assertThat(matcher.find()).as("expected %s in %s", pattern.pattern(), text).isTrue();
        return matcher.group(1);
    }

    private static String text(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private Session registerPersonal(String address) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/personal/register")
                        .with(csrf())
                        .with(as(address, null)))
                .andExpect(status().isCreated())
                .andReturn();
        String json = text(result);
        assertThat(json).doesNotContain("\"email\"", "\"phone\"", "\"displayName\"");
        return new Session((MockHttpSession) result.getRequest().getSession(false), address,
                first(SIGN_IN_ID, json));
    }

    private Organisation registerOrganisation(String address, String name) throws Exception {
        MvcResult result = mvc.perform(post("/api/organisations/register")
                        .with(csrf())
                        .with(as(address, null))
                        .contentType("application/json")
                        .content("{\"name\":\"" + name + "\",\"type\":\"COMMUNITY\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String json = text(result);
        Session admin = new Session((MockHttpSession) result.getRequest().getSession(false), address,
                first(SIGN_IN_ID, json));
        return new Organisation(first(ORG_ID, json), admin, first(ORG_CODE, json), first(RECOVERY_CODE, json));
    }

    private String submitCase(Session reporter, String organisationId) throws Exception {
        MvcResult result = mvc.perform(post("/api/personal/cases")
                        .with(csrf())
                        .with(as(reporter.address(), reporter.http()))
                        .contentType("application/json")
                        .content("{\"title\":\"Test report\",\"category\":\"Bullying\",\"urgency\":\"HIGH\","
                                + "\"description\":\"Dummy text for an automated test.\","
                                + "\"organisationId\":\"" + organisationId + "\",\"consentShared\":true}"))
                .andExpect(status().isCreated())
                .andReturn();
        return first(CASE_ID, text(result));
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mvc.perform(get("/api/health").with(as("198.51.100.5", null)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"UP\"")));
    }

    @Test
    void duplicateOrganisationNameIsRejectedWithoutCreatingAnotherWorkspace() throws Exception {
        Organisation first = registerOrganisation("198.51.100.13", "ABES Engineering College");
        mvc.perform(post("/api/organisations/register")
                        .with(csrf())
                        .with(as("198.51.100.14", null))
                        .contentType("application/json")
                        .content("{\"name\":\"  abes   engineering college \",\"type\":\"EDUCATIONAL_INSTITUTION\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("already registered")));

        MvcResult list = mvc.perform(get("/api/organisations").with(as("198.51.100.15", null)))
                .andExpect(status().isOk()).andReturn();
        assertThat(text(list)).contains(first.id()).contains(first.organisationCode());
        assertThat(text(list)).doesNotContain("ab es engineering");
       assertThat(text(list).split("ABES Engineering College", -1)).hasSize(2);
    }

    @Test
    void organisationRecoveryCreatesReplacementAdminAndRotatesTheRecoveryCode() throws Exception {
        Organisation org = registerOrganisation("198.51.100.16", "Org Recovery Flow");
        MvcResult recovered = mvc.perform(post("/api/organisations/recover")
                        .with(csrf())
                        .with(as("198.51.100.17", null))
                        .contentType("application/json")
                        .content("{\"organisationCode\":\"" + org.organisationCode()
                                + "\",\"recoveryCode\":\"" + org.recoveryCode() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String recoveredJson = text(recovered);
        assertThat(first(SIGN_IN_ID, recoveredJson)).startsWith("SE-");
        String replacementRecovery = first(RECOVERY_CODE, recoveredJson);
        assertThat(replacementRecovery).isNotEqualTo(org.recoveryCode());

        mvc.perform(post("/api/organisations/recover")
                        .with(csrf())
                        .with(as("198.51.100.18", null))
                        .contentType("application/json")
                        .content("{\"organisationCode\":\"" + org.organisationCode()
                                + "\",\"recoveryCode\":\"" + org.recoveryCode() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void personalRegistrationReturnsSignInIdAndLoginWorks() throws Exception {
        Session reporter = registerPersonal("198.51.100.10");
        assertThat(reporter.signInId()).startsWith("SE-").hasSize(46);

        mvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .with(as("198.51.100.10", null))
                        .contentType("application/json")
                        .content("{\"signInId\":\"" + reporter.signInId() + "\",\"workspace\":\"PERSONAL\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void wrongSignInIdReturnsGenericError() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .with(as("198.51.100.11", null))
                        .contentType("application/json")
                        .content("{\"signInId\":\"SE-" + "a".repeat(43) + "\",\"workspace\":\"PERSONAL\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("Sign-in failed")));
    }

    @Test
    void protectedRoutesRequireASession() throws Exception {
        mvc.perform(get("/api/personal/cases").with(as("198.51.100.12", null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reporterCannotReadAnotherReportersCase() throws Exception {
        Organisation org = registerOrganisation("198.51.100.20", "Org Alpha");
        Session owner = registerPersonal("198.51.100.21");
        Session stranger = registerPersonal("198.51.100.22");
        String caseId = submitCase(owner, org.id());

        mvc.perform(get("/api/personal/cases/" + caseId)
                        .with(as("198.51.100.22", stranger.http())))
                .andExpect(status().isNotFound());
    }

    @Test
    void responderFromAnotherOrganisationGetsNotFound() throws Exception {
        Organisation orgA = registerOrganisation("198.51.100.30", "Org A");
        Organisation orgB = registerOrganisation("198.51.100.31", "Org B");
        Session reporter = registerPersonal("198.51.100.32");
        String caseId = submitCase(reporter, orgA.id());

        mvc.perform(get("/api/org/cases/" + caseId)
                        .with(as("198.51.100.31", orgB.admin().http())))
                .andExpect(status().isNotFound());
    }

    @Test
    void messagesFlowBothWaysWithoutExposingAccountIds() throws Exception {
        Organisation organisation = registerOrganisation("198.51.100.40", "Org Messages");
        Session reporter = registerPersonal("198.51.100.41");
        String caseId = submitCase(reporter, organisation.id());

        mvc.perform(post("/api/personal/cases/" + caseId + "/messages")
                        .with(csrf())
                        .with(as("198.51.100.41", reporter.http()))
                        .contentType("application/json")
                        .content("{\"body\":\"Hello from reporter\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/org/cases/" + caseId + "/messages")
                        .with(csrf())
                        .with(as("198.51.100.40", organisation.admin().http()))
                        .contentType("application/json")
                        .content("{\"body\":\"Hello from team\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hello from reporter")))
                .andExpect(content().string(containsString("Hello from team")));

        MvcResult reporterView = mvc.perform(get("/api/personal/cases/" + caseId)
                        .with(as("198.51.100.41", reporter.http())))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(text(reporterView))
                .doesNotContain("authorAccountId", "reporterAccountId", "reporter_account_id");
    }

    @Test
    void evidenceRejectsExecutablesAcceptsTextAndDownloadsIt() throws Exception {
        Organisation organisation = registerOrganisation("198.51.100.50", "Org Evidence");
        Session reporter = registerPersonal("198.51.100.51");
        String caseId = submitCase(reporter, organisation.id());

        MockMultipartFile exe = new MockMultipartFile("file", "setup.exe",
                "application/octet-stream", new byte[]{'M', 'Z', 0, 0});
        mvc.perform(multipart("/api/personal/cases/" + caseId + "/evidence")
                        .file(exe)
                        .with(csrf())
                        .with(as("198.51.100.51", reporter.http())))
                .andExpect(status().isBadRequest());

        MockMultipartFile note = new MockMultipartFile("file", "notes.txt", "text/plain",
                "Dated notes of the incident".getBytes(StandardCharsets.UTF_8));
        MvcResult uploaded = mvc.perform(multipart("/api/personal/cases/" + caseId + "/evidence")
                        .file(note)
                        .with(csrf())
                        .with(as("198.51.100.51", reporter.http())))
                .andExpect(status().isCreated())
                .andReturn();
        String fileId = first(FILE_ID, text(uploaded));

        mvc.perform(get("/api/personal/cases/" + caseId + "/evidence/" + fileId)
                        .with(as("198.51.100.51", reporter.http())))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().string("Dated notes of the incident"));
    }

    @Test
    void logoutInvalidatesTheServerSideSession() throws Exception {
        Session reporter = registerPersonal("198.51.100.60");
        mvc.perform(post("/api/auth/logout")
                        .with(csrf())
                        .with(as("198.51.100.60", reporter.http())))
                .andExpect(status().isNoContent());
        assertThat(reporter.http().isInvalid()).isTrue();

        mvc.perform(get("/api/personal/cases").with(as("198.51.100.60", null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void repeatedWrongIdsAreThrottled() throws Exception {
        String wrong = "{\"signInId\":\"SE-" + "b".repeat(43) + "\",\"workspace\":\"PERSONAL\"}";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login")
                            .with(csrf())
                            .with(as("198.51.100.70", null))
                            .contentType("application/json")
                            .content(wrong))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .with(as("198.51.100.70", null))
                        .contentType("application/json")
                        .content(wrong))
                .andExpect(status().is(429));
    }
}
