package org.ggomes.myleolite

import org.ggomes.myleolite.domain.EnronDatasetLoader
import org.junit.Test
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy

class EnronDatasetTest {

    private val header = listOf("Folder-Name", "Date", "From", "To", "Subject", "Body", "Cc", "Bcc", "Extra")

    @Test
    fun rawString_joinsEightFields_whitespaceCollapsed() {
        val email = org.ggomes.myleolite.domain.EnronEmail(
            folderName = "maildir",
            date = "Thu,\n 16 Nov 2000",
            from = "a@b.com",
            to = "c@d.com",
            subject = "Hello",
            body = "line1\nline2",
            cc = "",
            bcc = "",
        )
        assertThat(email.rawString()).isEqualTo("maildir Thu, 16 Nov 2000 a@b.com c@d.com Hello line1 line2")
    }

    @Test
    fun parseEmails_mapsColumns() {
        val rows = listOf(listOf("f", "d", "a@b", "c@d", "s", "b", "cc", "bcc", "x"))
        val emails = EnronDatasetLoader.parseEmails(header, rows)
        assertThat(emails).hasSize(1)
        assertThat(emails[0].rawString()).isEqualTo("f d a@b c@d s b cc bcc")
    }

    @Test
    fun parseEmails_missingColumn_throws() {
        assertThatThrownBy {
            EnronDatasetLoader.parseEmails(listOf("From"), listOf(listOf("a")))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun loadFirst_capsCount() {
        val text = "Folder-Name,Date,From,To,Subject,Body,Cc,Bcc\na,b,c,d,e,f,g,h\ni,j,k,l,m,n,o,p"
        assertThat(EnronDatasetLoader.loadFirst(1, text)).hasSize(1)
        assertThat(EnronDatasetLoader.countRows(text)).isEqualTo(2)
    }
}
