package vn.edu.medmaintenance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.medmaintenance.api.dto.request.CreateAccountRequest;
import vn.edu.medmaintenance.api.dto.request.ResetAccountPasswordRequest;
import vn.edu.medmaintenance.persistence.enums.UserRole;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminAccountIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    private String admin;
    private final Map<Long, String> created = new LinkedHashMap<>();
    private final String prefix = "SMOKE-V2-ADMIN-TEST-" + UUID.randomUUID();
    private long noi;
    private long ngoai;
    private String password() { return UUID.randomUUID().toString(); }
    private String name() { return prefix + "-" + UUID.randomUUID().toString().substring(0, 8); }

    @BeforeAll void setup() {
        admin = demoToken("demo_admin", "ADMIN");
        noi = jdbc.queryForObject("SELECT id FROM department WHERE code='KHOA_NOI'", Long.class);
        ngoai = jdbc.queryForObject("SELECT id FROM department WHERE code='KHOA_NGOAI'", Long.class);
    }
    @AfterEach void cleanup() {
        for (var account : created.entrySet()) {
            assertThat(account.getValue().startsWith(prefix)).isTrue();
            assertThat(jdbc.update("DELETE FROM user_account WHERE id=? AND username=?", account.getKey(), account.getValue())).isEqualTo(1);
        }
        created.clear();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_account", Integer.class)).isEqualTo(15);
    }
    private ResponseEntity<JsonNode> send(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        if (body != null) headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }
    private JsonNode ok(ResponseEntity<JsonNode> response, int status) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        return response.getBody();
    }
    private void error(ResponseEntity<JsonNode> response, int status, String code) {
        var body = ok(response, status);
        assertThat(body.path("code").asText()).isEqualTo(code);
        assertThat(body.path("fieldErrors").isArray()).isTrue();
        assertThat(body.toString().contains("passwordHash") || body.toString().contains("stackTrace")).isFalse();
    }
    private void safe(JsonNode response) {
        Set<String> allowed = Set.of("id", "username", "role", "departmentId", "departmentCode", "departmentName", "active");
        response.fieldNames().forEachRemaining(key -> assertThat(allowed.contains(key)).isTrue());
        assertThat(response.path("id").asLong()).isPositive();
    }
    private String demoToken(String user, String role) {
        return login(user, System.getenv("DEMO_" + role + "_PASSWORD")).path("accessToken").asText();
    }
    private JsonNode login(String user, String password) {
        return ok(send(HttpMethod.POST, "/api/auth/login", null, Map.of("username", user, "password", password)), 200);
    }
    private Map<String,Object> body(String username, String password, UserRole role, Long department, boolean active) {
        Map<String,Object> body = new HashMap<>();
        body.put("username", username); body.put("password", password); body.put("role", role.name());
        body.put("departmentId", department); body.put("active", active); return body;
    }
    private Map<String,Object> edit(UserRole role, Long department) {
        Map<String,Object> body = new HashMap<>(); body.put("role", role.name()); body.put("departmentId", department); return body;
    }
    private JsonNode create(String username, String password, UserRole role, Long department, boolean active) {
        var account = ok(send(HttpMethod.POST, "/api/admin/accounts", admin, body(username, password, role, department, active)), 201);
        created.put(account.path("id").asLong(), account.path("username").asText()); safe(account); return account;
    }
    private String path(JsonNode account) { return "/api/admin/accounts/" + account.path("id").asLong(); }

    @Test void adminListAndDetailExposeOnlySafeFields() {
        var list = ok(send(HttpMethod.GET, "/api/admin/accounts?size=5", admin, null), 200);
        assertThat(list.path("content").size()).isEqualTo(5);
        list.path("content").forEach(this::safe);
        assertThat(list.path("totalElements").asInt()).isEqualTo(15);
        safe(ok(send(HttpMethod.GET, "/api/admin/accounts/4", admin, null), 200));
    }
    @Test void paginationSearchCombinedFiltersAndLiteralWildcardWork() {
        String username = name() + "_Pct%";
        var account = create(username, password(), UserRole.KHOA_PHONG, noi, false);
        var found = ok(send(HttpMethod.GET, "/api/admin/accounts?search=" + prefix.toLowerCase() + "&role=KHOA_PHONG&departmentId=" + noi + "&active=false", admin, null), 200);
        assertThat(found.path("totalElements").asInt()).isEqualTo(1);
        assertThat(found.at("/content/0/id").asLong()).isEqualTo(account.path("id").asLong());
        var literal = ok(send(HttpMethod.GET, "/api/admin/accounts?search=_Pct", admin, null), 200);
        assertThat(literal.path("totalElements").asInt()).isEqualTo(1);
        var a = ok(send(HttpMethod.GET, "/api/admin/accounts?page=0&size=3", admin, null), 200);
        var b = ok(send(HttpMethod.GET, "/api/admin/accounts?page=1&size=3", admin, null), 200);
        Set<Long> ids = new HashSet<>(); a.path("content").forEach(row -> ids.add(row.path("id").asLong()));
        b.path("content").forEach(row -> assertThat(ids.contains(row.path("id").asLong())).isFalse());
        var again = ok(send(HttpMethod.GET, "/api/admin/accounts?page=0&size=3", admin, null), 200);
        assertThat(again).isEqualTo(a);
    }
    @Test void allEndpointsRejectEachNonAdminAndAnonymous() {
        for (String[] role : new String[][]{{"demo_vtyt","VTYT"},{"demo_bgd","BGD"},{"demo_khoa_noi","KHOA"}}) {
            String token = demoToken(role[0],role[1]);
            error(send(HttpMethod.GET,"/api/admin/accounts",token,null),403,"ACCESS_DENIED");
            error(send(HttpMethod.GET,"/api/admin/accounts/4",token,null),403,"ACCESS_DENIED");
            error(send(HttpMethod.POST,"/api/admin/accounts",token,Map.of()),403,"ACCESS_DENIED");
            error(send(HttpMethod.PATCH,"/api/admin/accounts/4",token,Map.of()),403,"ACCESS_DENIED");
            for (String command : List.of("activate","deactivate","reset-password"))
                error(send(HttpMethod.POST,"/api/admin/accounts/4/"+command,token,Map.of()),403,"ACCESS_DENIED");
        }
        error(send(HttpMethod.GET,"/api/admin/accounts",null,null),401,"AUTHENTICATION_REQUIRED");
    }
    @Test void createsAllFourRolesWithRealLoginBcryptAndTrimmedUsername() {
        for (UserRole role : UserRole.values()) {
            String username=name(), raw=password();
            var account=create("  "+username+"  ",raw,role,role==UserRole.KHOA_PHONG?noi:null,true);
            assertThat(account.path("username").asText()).isEqualTo(username);
            String hash=jdbc.queryForObject("SELECT password_hash FROM user_account WHERE id=?",String.class,account.path("id").asLong());
            assertThat(hash.matches("\\$2[aby]\\$12\\$.+")).isTrue();
            assertThat(hash.equals(raw)).isFalse(); assertThat(encoder.matches(raw,hash)).isTrue();
            var logged=login(username,raw); assertThat(logged.at("/user/role").asText()).isEqualTo(role.name());
            var me=ok(send(HttpMethod.GET,"/api/auth/me",logged.path("accessToken").asText(),null),200);
            assertThat(me.path("role").asText()).isEqualTo(role.name());
            assertThat(account.toString().contains(raw) || account.toString().contains(hash)).isFalse();
        }
    }
    @Test void directShortPasswordAndExactWhitespaceAreSupported() {
        String raw=Integer.toString(100000+new java.security.SecureRandom().nextInt(900000));
        var account=create(name(),raw,UserRole.BAN_GIAM_DOC,null,true);
        login(account.path("username").asText(),raw);
        String spaced=" " + password() + " ";
        var other=create(name(),spaced,UserRole.ADMIN,null,true);
        login(other.path("username").asText(),spaced);
        error(send(HttpMethod.POST,"/api/auth/login",null,Map.of("username",other.path("username").asText(),"password",spaced.trim())),401,"INVALID_CREDENTIALS");
    }
    @Test void duplicateUsernameReturns409WithoutExtraRow() {
        String username=name(); create(username,password(),UserRole.PHONG_VTYT,null,true);
        error(send(HttpMethod.POST,"/api/admin/accounts",admin,body(" "+username+" ",password(),UserRole.ADMIN,null,true)),409,"USERNAME_ALREADY_EXISTS");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_account WHERE username=?",Integer.class,username)).isEqualTo(1);
    }
    @Test void requiredInvalidAndInactiveDepartmentRejectWithoutPersistence() {
        String username=name();
        error(send(HttpMethod.POST,"/api/admin/accounts",admin,body(username,password(),UserRole.KHOA_PHONG,null,true)),400,"DEPARTMENT_REQUIRED");
        for (long id : List.of(0L,999999999L))
            error(send(HttpMethod.POST,"/api/admin/accounts",admin,body(username,password(),UserRole.KHOA_PHONG,id,true)),400,"INVALID_DEPARTMENT");
        long inactive=jdbc.queryForObject("INSERT INTO department(code,name,active) VALUES (?, 'Temporary test department', false) RETURNING id",Long.class,prefix);
        try { error(send(HttpMethod.POST,"/api/admin/accounts",admin,body(username,password(),UserRole.KHOA_PHONG,inactive,true)),400,"INVALID_DEPARTMENT"); }
        finally { jdbc.update("DELETE FROM department WHERE id=? AND code=?",inactive,prefix); }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_account WHERE username=?",Integer.class,username)).isZero();
    }
    @Test void invalidRoleUsernameStatusAndPasswordReturnSafe400() {
        for (String key : List.of("username","password","role","active")) {
            var request=body(name(),password(),UserRole.ADMIN,null,true); request.put(key,key.equals("username")||key.equals("password")?" ":null);
            error(send(HttpMethod.POST,"/api/admin/accounts",admin,request),400,"VALIDATION_ERROR");
        }
        var invalid=body(name(),password(),UserRole.ADMIN,null,true); invalid.put("role","NOT_A_ROLE");
        error(send(HttpMethod.POST,"/api/admin/accounts",admin,invalid),400,"INVALID_PARAMETER");
        error(send(HttpMethod.POST,"/api/admin/accounts",admin,body(name(),"界".repeat(25),UserRole.ADMIN,null,true)),400,"INVALID_PASSWORD");
        error(send(HttpMethod.GET,"/api/admin/accounts?size=0",admin,null),400,"VALIDATION_ERROR");
        error(send(HttpMethod.GET,"/api/admin/accounts?sort=passwordHash",admin,null),400,"INVALID_PARAMETER");
    }
    @Test void deactivateBlocksLoginAndExistingTokenActivateRestores() {
        String raw=password(); var account=create(name(),raw,UserRole.KHOA_PHONG,noi,true);
        String token=login(account.path("username").asText(),raw).path("accessToken").asText();
        assertThat(ok(send(HttpMethod.POST,path(account)+"/deactivate",admin,null),200).path("active").asBoolean()).isFalse();
        error(send(HttpMethod.GET,"/api/auth/me",token,null),401,"INVALID_TOKEN");
        error(send(HttpMethod.POST,"/api/auth/login",null,Map.of("username",account.path("username").asText(),"password",raw)),401,"INVALID_CREDENTIALS");
        assertThat(ok(send(HttpMethod.POST,path(account)+"/activate",admin,null),200).path("active").asBoolean()).isTrue();
        login(account.path("username").asText(),raw);
    }
    @Test void createInactiveCannotLoginUntilActivated() {
        String raw=password(); var account=create(name(),raw,UserRole.PHONG_VTYT,null,false);
        error(send(HttpMethod.POST,"/api/auth/login",null,Map.of("username",account.path("username").asText(),"password",raw)),401,"INVALID_CREDENTIALS");
        ok(send(HttpMethod.POST,path(account)+"/activate",admin,null),200); login(account.path("username").asText(),raw);
    }
    @Test void resetPasswordRejectsOldAllowsNewAndPreservesStatusRoleDepartment() {
        String old=password(), next=password(); var account=create(name(),old,UserRole.KHOA_PHONG,noi,true);
        String existing=login(account.path("username").asText(),old).path("accessToken").asText();
        safe(ok(send(HttpMethod.POST,path(account)+"/reset-password",admin,Map.of("newPassword",next)),200));
        error(send(HttpMethod.POST,"/api/auth/login",null,Map.of("username",account.path("username").asText(),"password",old)),401,"INVALID_CREDENTIALS");
        login(account.path("username").asText(),next);
        var me=ok(send(HttpMethod.GET,"/api/auth/me",existing,null),200); // unchanged V1 JWT lifetime model
        assertThat(me.path("departmentId").asLong()).isEqualTo(noi);
        String hash=jdbc.queryForObject("SELECT password_hash FROM user_account WHERE id=?",String.class,account.path("id").asLong());
        assertThat(encoder.matches(next,hash)).isTrue(); assertThat(encoder.matches(old,hash)).isFalse();
    }
    @Test void failedResetLeavesOriginalPasswordValid() {
        String raw=password(); var account=create(name(),raw,UserRole.ADMIN,null,true);
        error(send(HttpMethod.POST,path(account)+"/reset-password",admin,Map.of("newPassword"," ")),400,"VALIDATION_ERROR");
        error(send(HttpMethod.POST,path(account)+"/reset-password",admin,Map.of("newPassword","界".repeat(25))),400,"INVALID_PASSWORD");
        login(account.path("username").asText(),raw);
    }
    @Test void editsRoleDepartmentOnExistingTokenAndKeepsUsernameStatus() {
        String raw=password(); var account=create(name(),raw,UserRole.KHOA_PHONG,noi,true);
        String token=login(account.path("username").asText(),raw).path("accessToken").asText();
        ok(send(HttpMethod.GET,"/api/equipment/1/maintenance-history",token,null),200);
        error(send(HttpMethod.GET,"/api/equipment/4/maintenance-history",token,null),403,"EQUIPMENT_HISTORY_ACCESS_DENIED");
        safe(ok(send(HttpMethod.PATCH,path(account),admin,edit(UserRole.KHOA_PHONG,ngoai)),200));
        assertThat(ok(send(HttpMethod.GET,"/api/auth/me",token,null),200).path("departmentId").asLong()).isEqualTo(ngoai);
        error(send(HttpMethod.GET,"/api/equipment/1/maintenance-history",token,null),403,"EQUIPMENT_HISTORY_ACCESS_DENIED");
        var update=edit(UserRole.BAN_GIAM_DOC,null); update.put("username","ignored-change"); update.put("active",false);
        var edited=ok(send(HttpMethod.PATCH,path(account),admin,update),200);
        assertThat(edited.path("username").asText()).isEqualTo(account.path("username").asText());
        assertThat(edited.path("active").asBoolean()).isTrue(); assertThat(edited.path("departmentId").isNull()).isTrue();
        assertThat(ok(send(HttpMethod.GET,"/api/auth/me",token,null),200).path("role").asText()).isEqualTo("BAN_GIAM_DOC");
        ok(send(HttpMethod.GET,"/api/approvals/pending",token,null),200);
        login(account.path("username").asText(),raw);
    }
    @Test void invalidEditRollsBackAllAccountFields() {
        String raw=password(); var account=create(name(),raw,UserRole.PHONG_VTYT,noi,true);
        error(send(HttpMethod.PATCH,path(account),admin,edit(UserRole.KHOA_PHONG,null)),400,"DEPARTMENT_REQUIRED");
        assertThat(ok(send(HttpMethod.GET,path(account),admin,null),200)).isEqualTo(account);
        login(account.path("username").asText(),raw);
    }
    @Test void selfDeactivationBlockedAndNoMaintenancePrivilegeOrDeleteAdded() {
        String raw=password(); var account=create(name(),raw,UserRole.ADMIN,null,true);
        String own=login(account.path("username").asText(),raw).path("accessToken").asText();
        error(send(HttpMethod.POST,path(account)+"/deactivate",own,null),409,"ACCOUNT_SELF_DEACTIVATION_FORBIDDEN");
        assertThat(ok(send(HttpMethod.GET,path(account),own,null),200).path("active").asBoolean()).isTrue();
        error(send(HttpMethod.POST,"/api/plans",own,Map.of()),403,"ACCESS_DENIED");
        error(send(HttpMethod.GET,"/api/equipment/1/maintenance-history",own,null),403,"ACCESS_DENIED");
        error(send(HttpMethod.DELETE,path(account),admin,null),405,"METHOD_NOT_ALLOWED");
    }
    @Test void missingAccount404AndRequestDebugRenderingRedactsPasswords() {
        error(send(HttpMethod.GET,"/api/admin/accounts/999999999",admin,null),404,"ACCOUNT_NOT_FOUND");
        error(send(HttpMethod.PATCH,"/api/admin/accounts/999999999",admin,edit(UserRole.ADMIN,null)),404,"ACCOUNT_NOT_FOUND");
        for (String command : List.of("activate","deactivate"))
            error(send(HttpMethod.POST,"/api/admin/accounts/999999999/"+command,admin,null),404,"ACCOUNT_NOT_FOUND");
        error(send(HttpMethod.POST,"/api/admin/accounts/999999999/reset-password",admin,Map.of("newPassword",password())),404,"ACCOUNT_NOT_FOUND");
        String raw=password();
        assertThat(new CreateAccountRequest(name(),raw,UserRole.ADMIN,null,true).toString().contains(raw)).isFalse();
        assertThat(new ResetAccountPasswordRequest(raw).toString().contains(raw)).isFalse();
    }
}
