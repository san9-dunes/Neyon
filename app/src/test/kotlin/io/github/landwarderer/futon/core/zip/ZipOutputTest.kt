package io.github.landwarderer.futon.core.zip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ZipOutputTest {

	@get:Rule
	val tempFolder = TemporaryFolder()

	@Test
	fun testPutString() {
		val zipFile = tempFolder.newFile("test_put_string.zip")
		ZipOutput(zipFile).use { zipOutput ->
			assertTrue(zipOutput.put("test.txt", "Hello, World!"))
		}

		ZipFile(zipFile).use { zip ->
			val entry = zip.getEntry("test.txt")
			assertNotNull(entry)
			val content = zip.getInputStream(entry).reader().readText()
			assertEquals("Hello, World!", content)
		}
	}

	@Test
	fun testPutFile() {
		val zipFile = tempFolder.newFile("test_put_file.zip")
		val fileToZip = tempFolder.newFile("source.txt")
		fileToZip.writeText("File Content")

		ZipOutput(zipFile).use { zipOutput ->
			assertTrue(zipOutput.put("renamed.txt", fileToZip))
		}

		ZipFile(zipFile).use { zip ->
			val entry = zip.getEntry("renamed.txt")
			assertNotNull(entry)
			val content = zip.getInputStream(entry).reader().readText()
			assertEquals("File Content", content)
		}
	}

	@Test
	fun testPutDirectory() {
		val zipFile = tempFolder.newFile("test_put_dir.zip")
		val dirToZip = tempFolder.newFolder("source_dir")
		val childFile1 = File(dirToZip, "child1.txt")
		childFile1.writeText("Child 1")
		val subDir = File(dirToZip, "sub")
		subDir.mkdir()
		val childFile2 = File(subDir, "child2.txt")
		childFile2.writeText("Child 2")

		ZipOutput(zipFile).use { zipOutput ->
			assertTrue(zipOutput.put("dest_dir", dirToZip))
		}

		ZipFile(zipFile).use { zip ->
			// Check directory entry
			val dirEntry = zip.getEntry("dest_dir/")
			assertNotNull(dirEntry)
			assertTrue(dirEntry.isDirectory)

			// Check child1
			val entry1 = zip.getEntry("dest_dir/child1.txt")
			assertNotNull(entry1)
			assertEquals("Child 1", zip.getInputStream(entry1).reader().readText())

			// Check child2
			val entry2 = zip.getEntry("dest_dir/sub/child2.txt")
			assertNotNull(entry2)
			assertEquals("Child 2", zip.getInputStream(entry2).reader().readText())
		}
	}

	@Test
	fun testAddDirectory() {
		val zipFile = tempFolder.newFile("test_add_dir.zip")
		ZipOutput(zipFile).use { zipOutput ->
			assertTrue(zipOutput.addDirectory("my_dir"))
			assertTrue(zipOutput.addDirectory("my_dir2/"))
		}

		ZipFile(zipFile).use { zip ->
			val entry1 = zip.getEntry("my_dir/")
			assertNotNull(entry1)
			assertTrue(entry1.isDirectory)

			val entry2 = zip.getEntry("my_dir2/")
			assertNotNull(entry2)
			assertTrue(entry2.isDirectory)
		}
	}

	@Test
	fun testCopyEntryFrom() {
		val sourceZipFile = tempFolder.newFile("source.zip")
		ZipOutputStream(FileOutputStream(sourceZipFile)).use { zos ->
			zos.putNextEntry(ZipEntry("source_entry.txt"))
			zos.write("Source Content".toByteArray())
			zos.closeEntry()
		}

		val destZipFile = tempFolder.newFile("dest.zip")
		ZipOutput(destZipFile).use { zipOutput ->
			ZipFile(sourceZipFile).use { sourceZip ->
				val entry = sourceZip.getEntry("source_entry.txt")
				assertTrue(zipOutput.copyEntryFrom(sourceZip, entry))
			}
		}

		ZipFile(destZipFile).use { zip ->
			val entry = zip.getEntry("source_entry.txt")
			assertNotNull(entry)
			val content = zip.getInputStream(entry).reader().readText()
			assertEquals("Source Content", content)
		}
	}

	@Test
	fun testDuplicateEntries() {
		val zipFile = tempFolder.newFile("test_duplicate.zip")
		ZipOutput(zipFile).use { zipOutput ->
			assertTrue(zipOutput.put("dup.txt", "First"))
			assertFalse(zipOutput.put("dup.txt", "Second"))

			assertTrue(zipOutput.addDirectory("dup_dir"))
			assertFalse(zipOutput.addDirectory("dup_dir"))
		}
	}
}
