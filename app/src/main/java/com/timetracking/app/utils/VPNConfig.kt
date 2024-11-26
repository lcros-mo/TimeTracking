data class VPNCredentials(
    val username: String,
    val password: String
)

object VPNConfig {
    const val SERVER_ADDRESS = "80.32.125.224"
    private const val USERNAME = "admin"
    private const val PASSWORD = "grecapp2024"
    const val PROTOCOL = "pptp"

    fun getCredentials() = VPNCredentials(USERNAME, PASSWORD)
}