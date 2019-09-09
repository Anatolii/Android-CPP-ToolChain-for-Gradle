import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class GitRepository(repositoryDir: File, private val masterBranchName: String = "master") {
    private val repository: Repository = FileRepositoryBuilder.create(File(repositoryDir, ".git"))

    fun generateCalVer(): String =
            listOfNotNull(
                    repository.branch.takeIf { isOnMasterBranch().not() },
                    lastCommitTimestamp()?.fullPaddedDateTime()
            ).joinToString(separator = "-").replace(oldChar = '/', newChar = '_')

    private fun lastCommit(): RevCommit? {
        return repository.refDatabase.refs.find { it.name == repository.fullBranch }?.objectId
                ?.let { repository.parseCommit(it) }
    }

    private fun lastCommitTimestamp(): LocalDateTime? {
        return lastCommit()?.commitTime?.toLong()
                ?.let { Instant.ofEpochSecond(it) }
                ?.let { LocalDateTime.ofInstant(it, ZoneOffset.UTC) }
    }


    private fun isOnMasterBranch() = repository.branch == masterBranchName
}


private fun Int.padZerros(length: Int = 2, radix: Int = 10) = toString(radix).padStart(length, '0')
private fun LocalDateTime.fullPaddedDate() = "${year}${monthValue.padZerros()}${dayOfMonth.padZerros()}"
private fun LocalDateTime.fullPaddedTime() = "${hour.padZerros()}${minute.padZerros()}${second.padZerros()}"
private fun LocalDateTime.fullPaddedDateTime() = "${fullPaddedDate()}.${fullPaddedTime()}"