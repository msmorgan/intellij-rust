/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model.impl

import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BuildViewTestFixture
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.toolwindow.CargoToolWindow
import org.rust.fileTree
import org.rust.launchAction

class SyncToolWindowTest : RsWithToolchainTestBase() {

    private lateinit var buildViewTestFixture: BuildViewTestFixture

    override fun setUp() {
        super.setUp()
        buildViewTestFixture = BuildViewTestFixture(project)
        buildViewTestFixture.setUp()
    }

    override fun tearDown() {
        buildViewTestFixture.tearDown()
        super.tearDown()
    }

    fun `test single project`() {
        val project = buildProject {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    fn main() {}
                """)
            }
        }
        checkSyncViewTree("""
            -
             -finished
              -Sync ${project.root.name} project
               Getting toolchain version
               Updating workspace info
               Getting Rust stdlib
        """)
    }

    fun `test several cargo projects 1`() {
        val project = buildProject {
            toml("Cargo.toml", """
                [package]
                name = "crate1"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    fn main() {}
                """)
            }
            dir("crate2") {
                toml("Cargo.toml", """
                    [package]
                    name = "crate2"
                    version = "0.1.0"
                    authors = []
                """)

                dir("src") {
                    rust("main.rs", """
                        fn main() {}
                    """)
                }
            }
        }

        attachCargoProject(project.root.findChild("crate2")!!)

        checkSyncViewTree("""
            -
             -finished
              -Sync crate1 project
               Getting toolchain version
               Updating workspace info
               Getting Rust stdlib
              -Sync crate2 project
               Getting toolchain version
               Updating workspace info
               Getting Rust stdlib
        """)
    }

    fun `test several cargo projects 2`() {
        val project = buildProject {
            dir("crate1") {
                toml("Cargo.toml", """
                    [package]
                    name = "crate1"
                    version = "0.1.0"
                    authors = []
                """)

                dir("src") {
                    rust("main.rs", """
                        fn main() {}
                    """)
                }
            }
            dir("crate2") {
                toml("Cargo.toml", """
                    [package]
                    name = "crate2"
                    version = "0.1.0"
                    authors = []
                """)

                dir("src") {
                    rust("main.rs", """
                        fn main() {}
                    """)
                }
            }
        }
        attachCargoProject(project.root.findChild("crate1")!!)
        attachCargoProject(project.root.findChild("crate2")!!)

        checkSyncViewTree("""
            -
             -finished
              -Sync crate1 project
               Getting toolchain version
               Updating workspace info
               Getting Rust stdlib
              -Sync crate2 project
               Getting toolchain version
               Updating workspace info
               Getting Rust stdlib
        """)
    }

    fun `test with error in manifest`() {
        val project = buildProject {
            toml("Cargo.toml", """
                [package]
                name = "hello
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    fn main() {}
                """)
            }
        }
        checkSyncViewTree("""
            -
             -failed
              -Sync ${project.root.name} project
               Getting toolchain version
               -Updating workspace info
                Failed to run Cargo
               Getting Rust stdlib
        """)
    }

    fun `test no projects`() {
        val testProject = fileTree {
            dir("crate") {
                toml("Cargo.toml", """
                    [package]
                    name = "crate"
                    version = "0.1.0"
                    authors = []
                """)

                dir("src") {
                    rust("main.rs", """
                        fn main() {}
                    """)
                }
            }
        }.create(project, cargoProjectDirectory)
        val crateRoot = testProject.root.findChild("crate")!!
        attachCargoProject(crateRoot)
        val cargoProject = project.testCargoProjects.refreshAllProjectsSync().single()
        detachCargoProject(cargoProject)

        // This refresh shouldn't change Sync view since there isn't any Cargo project
        project.testCargoProjects.refreshAllProjectsSync()

        checkSyncViewTree("""
            -
             -finished
              -Sync crate project
               Getting toolchain version
               Updating workspace info
               Getting Rust stdlib
        """)
    }

    private fun attachCargoProject(cargoProjectRoot: VirtualFile) {
        myFixture.launchAction("Cargo.AttachCargoProject", PlatformDataKeys.VIRTUAL_FILE to cargoProjectRoot)
    }

    private fun detachCargoProject(cargoProject: CargoProject) {
        myFixture.launchAction("Cargo.DetachCargoProject", CargoToolWindow.SELECTED_CARGO_PROJECT to cargoProject)
    }

    private fun checkSyncViewTree(expected: String) {
        project.testCargoProjects.discoverAndRefreshSync()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        buildViewTestFixture.assertSyncViewTreeEquals(expected.trimIndent())
    }
}
