package com.izforge.izpack.integration.windows;

import static com.izforge.izpack.integration.windows.WindowsHelper.registryDeleteUninstallKey;
import static com.izforge.izpack.integration.windows.WindowsHelper.registryKeyExists;
import static com.izforge.izpack.util.Platform.Name.WINDOWS;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.izforge.izpack.api.data.AutomatedInstallData;
import com.izforge.izpack.api.exception.NativeLibException;
import com.izforge.izpack.compiler.container.TestConsoleInstallationContainer;
import com.izforge.izpack.compiler.container.TestConsoleInstallerContainer;
import com.izforge.izpack.core.os.RegistryDefaultHandler;
import com.izforge.izpack.core.os.RegistryHandler;
import com.izforge.izpack.event.RegistryInstallerListener;
import com.izforge.izpack.event.RegistryUninstallerListener;
import com.izforge.izpack.installer.bootstrap.Installer;
import com.izforge.izpack.installer.console.TestConsoleInstaller;
import com.izforge.izpack.installer.container.impl.ConsoleInstallerContainer;
import com.izforge.izpack.integration.console.AbstractConsoleInstallationTest;
import com.izforge.izpack.test.Container;
import com.izforge.izpack.test.InstallFile;
import com.izforge.izpack.test.RunOn;
import com.izforge.izpack.test.junit.PicoRunner;
import com.izforge.izpack.test.util.TestConsole;
import com.izforge.izpack.util.FileUtil;
import com.izforge.izpack.util.Platforms;
import com.izforge.izpack.util.PrivilegedRunner;


/**
 * Test installation on Windows.
 * <p/>
 * Verifies that:
 * <ul>
 * <li>An <em>Uninstall</em> entry is added to the registry by {@link RegistryInstallerListener} during
 * installation</li>
 * <li>The <em>Uninstall</em> entry is removed at uninstallation by {@link RegistryUninstallerListener}</li>
 * </ul>
 *
 * @author Tim Anderson
 */
@RunWith(PicoRunner.class)
@RunOn(WINDOWS)
@Container(TestConsoleInstallationContainer.class)
public class WindowsConsoleInstallationTest extends AbstractConsoleInstallationTest
{

    /**
     * The installer.
     */
    private final TestConsoleInstaller installer;

    /**
     * The registry handler.
     */
    private final RegistryDefaultHandler handler;

    /**
     * The app name.
     */
    private static final String APP_NAME = "IzPack Windows Console Installation Test 1.0";


    /**
     * Default uninstallation key.
     */
    private static final String DEFAULT_UNINSTALL_KEY = RegistryHandler.UNINSTALL_ROOT + APP_NAME;

    /**
     * Second installation uninstallation key.
     */
    private static final String UNINSTALL_KEY2 = RegistryHandler.UNINSTALL_ROOT + APP_NAME + "(1)";

    /**
     * Registry uninstallation keys. Hard-coded so we don't delete too much on cleanup if something unexpected happens.
     */
    private static final String[] UNINSTALL_KEYS = {DEFAULT_UNINSTALL_KEY, UNINSTALL_KEY2};


    /**
     * Constructs a <tt>WindowsConsoleInstallationTest</tt>
     *
     * @param installer   the installer
     * @param installData the installation date
     * @param handler     the registry handler
     * @throws Exception for any error
     */
    public WindowsConsoleInstallationTest(TestConsoleInstaller installer, AutomatedInstallData installData,
                                          RegistryDefaultHandler handler)
            throws Exception
    {
        super(installData);
        this.installer = installer;
        this.handler = handler;
    }


    /**
     * Sets up the test case.
     *
     * @throws Exception for any error
     */
    @Override
    public void setUp() throws Exception
    {
        assertFalse("This test must be run as administrator, or with Windows UAC turned off",
                    new PrivilegedRunner(Platforms.WINDOWS).isElevationNeeded());
        super.setUp();
        String appName = getInstallData().getInfo().getAppName();
        assertNotNull(appName);
        File file = FileUtil.getLockFile(appName);
        if (file.exists())
        {
            assertTrue(file.delete());
        }

        destroyRegistryEntries();
    }

    /**
     * Cleans up after the test.
     *
     * @throws Exception for any error
     */
    @After
    public void tearDown() throws Exception
    {
        destroyRegistryEntries();
    }

    /**
     * Runs the console installer against a script, and verifies expected files are installed.
     *
     * @throws Exception for any error
     */
    @Test
    @InstallFile("samples/windows/consoleinstall.xml")
    public void testInstallation() throws Exception
    {
        // run the install
        checkInstall();
        assertTrue(registryKeyExists(handler, DEFAULT_UNINSTALL_KEY));

        // run the uninstaller and verify that uninstall key is removed
        File uninstaller = getUninstallerJar();
        assertTrue(uninstaller.exists());
        runDestroyer(uninstaller);

        assertFalse(registryKeyExists(handler, DEFAULT_UNINSTALL_KEY));
    }

    /**
     * Runs the console installer twice, verifying that a second uninstall key is created.
     *
     * @throws Exception for any error
     */
    @Test
    @InstallFile("samples/windows/consoleinstall.xml")
    public void testMultipleInstallation() throws Exception
    {
        // run the install
        checkInstall();

        // remove the lock file to enable second installation
        removeLock();

        // run the installation again
        ConsoleInstallerContainer container2 = new TestConsoleInstallerContainer();
        TestConsoleInstaller installer2 = container2.getComponent(TestConsoleInstaller.class);
        TestConsole console2 = installer2.getConsole();
        console2.addScript("CheckedHelloPanel", "y", "1");
        console2.addScript("InfoPanel", "1");
        console2.addScript("TargetPanel", "\n", "1");

        assertFalse(registryKeyExists(handler, UNINSTALL_KEY2));
        checkInstall(installer2, container2.getComponent(AutomatedInstallData.class));

        // verify a second key is created
        assertTrue(registryKeyExists(handler, UNINSTALL_KEY2));
    }

    /**
     * Runs the console installer twice, verifying that a second uninstall key is created.
     *
     * @throws Exception for any error
     */
    @Test
    @InstallFile("samples/windows/consoleinstall.xml")
    public void testRejectMultipleInstallation() throws Exception
    {
        checkInstall();

        removeLock();

        ConsoleInstallerContainer container2 = new TestConsoleInstallerContainer();
        TestConsoleInstaller installer2 = container2.getComponent(TestConsoleInstaller.class);
        TestConsole console2 = installer2.getConsole();
        console2.addScript("CheckedHelloPanel", "n");

        assertFalse(registryKeyExists(handler, UNINSTALL_KEY2));
        installer2.run(Installer.CONSOLE_INSTALL, null);

        // verify the installation thinks it was unsuccessful
        assertFalse(container2.getComponent(AutomatedInstallData.class).isInstallSuccess());

        // make sure the script has completed
        TestConsole console = installer2.getConsole();
        assertTrue("Script still running panel: " + console.getScriptName(), console.scriptCompleted());

        // verify the second registry key hasn't been created
        assertFalse(registryKeyExists(handler, UNINSTALL_KEY2));
    }

    /**
     * Runs the installation, and verifies the uninstall key is created.
     *
     * @throws NativeLibException for any native library exception
     */
    private void checkInstall() throws NativeLibException
    {
        assertFalse(registryKeyExists(handler, DEFAULT_UNINSTALL_KEY));

        TestConsole console1 = installer.getConsole();
        console1.addScript("CheckedHelloPanel", "1");
        console1.addScript("InfoPanel", "1");
        console1.addScript("TargetPanel", "\n", "1");

        checkInstall(installer, getInstallData());

        assertTrue(registryKeyExists(handler, DEFAULT_UNINSTALL_KEY));
    }

    /**
     * Removes the lock file (which normally gets removed on exit), to enable multiple installs.
     */
    private void removeLock()
    {
        String appName = getInstallData().getInfo().getAppName();
        File file = FileUtil.getLockFile(appName);
        if (file.exists())
        {
            assertTrue(file.delete());
        }
    }


    /**
     * Destroys registry entries that may not have been cleared out by a previous run.
     *
     * @throws NativeLibException if the entries cannot be removed
     */
    private void destroyRegistryEntries() throws NativeLibException
    {
        for (String key : UNINSTALL_KEYS)
        {
            registryDeleteUninstallKey(handler, key);
        }
    }
}

