package nl.vdzon.robbertsassistent.auth

data class GoogleLoginRequest(val idToken: String = "")
data class LoginResponse(val token: String, val username: String)
