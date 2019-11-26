import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.RevCommit
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class GitRepository(repositoryDir: File, private val masterBranchName: String = "master") {
    private val git: Git = Git.open(repositoryDir)

    protected fun finalize() {
        git.close()
    }

    fun generateCalVer(): String =
            listOfNotNull(
                    git.repository.branch.takeIf { isOnMasterBranch().not() },
                    lastCommitTimestamp()?.fullPaddedDateTime()
            ).joinToString(separator = "-").replace(oldChar = '/', newChar = '-')

    private fun lastCommit(): RevCommit? {
        return git.repository.refDatabase.refs.find { it.name == "HEAD" }?.objectId
                ?.let { git.repository.parseCommit(it) }
    }

    private fun lastCommitTimestamp(): LocalDateTime? {
        return lastCommit()?.commitTime?.toLong()
                ?.let { Instant.ofEpochSecond(it) }
                ?.let { LocalDateTime.ofInstant(it, ZoneOffset.UTC) }
    }


    private fun isOnMasterBranch() = git.repository.branch == masterBranchName
}


private fun Int.padZerros(length: Int = 2, radix: Int = 10) = toString(radix).padStart(length, '0')
private fun LocalDateTime.fullPaddedDate() = "${year}${monthValue.padZerros()}${dayOfMonth.padZerros()}"
private fun LocalDateTime.fullPaddedTime() = "${hour.padZerros()}${minute.padZerros()}${second.padZerros()}"
private fun LocalDateTime.fullPaddedDateTime() = "${fullPaddedDate()}.${fullPaddedTime()}"