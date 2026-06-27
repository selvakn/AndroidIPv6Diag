package selvakn.ipv6diag.upload

sealed class UploadStatus {
    object Idle : UploadStatus()
    object Uploading : UploadStatus()
    object Success : UploadStatus()
    data class Failed(val reason: String) : UploadStatus()
}
