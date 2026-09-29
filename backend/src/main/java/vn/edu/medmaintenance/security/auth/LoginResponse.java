package vn.edu.medmaintenance.security.auth;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, AuthenticatedUserResponse user) {
    @Override
    public String toString() {
        return "LoginResponse[accessToken=[REDACTED], tokenType=" + tokenType
                + ", expiresIn=" + expiresIn + ", user=" + user + "]";
    }
}
