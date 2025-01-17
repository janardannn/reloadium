package rw.tests.unit;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import rw.consts.Const;
import rw.tests.BaseMockedTestCase;
import rw.tests.utils.MiscUtils;
import rw.tests.fixtures.PackageFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestService extends BaseMockedTestCase {

    @BeforeEach
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Test
    public void testBuiltinInstalledOnStart() throws Exception {
        this.service.init();
        MiscUtils.assertInstalled(this.builtinVersion);
    }

    @Test
    public void testUpdatingPeriodically() throws Exception {
        this.service.init();
        MiscUtils.assertInstalled(this.builtinVersion);
        service.checkForUpdate();
        MiscUtils.assertInstalled(this.webVersion);
    }

    @Test
    public void testNotUpdatingErrored() throws Exception {
        this.service.init();
        lenient().doThrow(new RuntimeException("Error")).when(
                this.service.webPackageManager).getWheelUrlsForVersion(any());

        this.service.checkForUpdate();

        verify(this.service.webPackageManager, times(1)).install(any());
        assertThat(this.service.webPackageManager.hasErrored()).isTrue();
        MiscUtils.assertInstalled(this.builtinVersion);
    }

    @Test
    public void testInstallingOnMissing() throws Exception {
        this.service.init();
        PackageFixture packageFixture = new PackageFixture(this.webVersion.toString());

        FileUtils.deleteDirectory(Const.get().getPackagesRootDir());

        this.service.checkIfStillGood();

        verify(this.service.builtinPackageManager,
                times(1)).install(any());

        MiscUtils.assertInstalled(this.webVersion);
    }
}
