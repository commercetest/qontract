package application

import com.ginsberg.junit.exit.ExpectSystemExitWithStatus
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE
import picocli.CommandLine
import picocli.CommandLine.IFactory
import run.qontract.core.Feature
import run.qontract.core.QONTRACT_EXTENSION
import run.qontract.mock.ScenarioStub
import run.qontract.stub.HttpClientFactory
import run.qontract.stub.HttpStubData
import java.io.File
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createFile
import kotlin.io.path.writeText

@SpringBootTest(webEnvironment = NONE, classes = [QontractApplication::class, StubCommand::class, HttpClientFactory::class])
internal class StubCommandTest {
    @MockkBean
    lateinit var qontractConfig: QontractConfig

    @MockkBean
    lateinit var fileOperations: FileOperations

    @Autowired
    lateinit var factory: IFactory

    @MockkBean
    lateinit var watchMaker: WatchMaker

    @MockkBean(relaxUnitFun = true)
    lateinit var watcher: Watcher

    @MockkBean
    lateinit var httpStubEngine: HTTPStubEngine

    @MockkBean
    lateinit var kafkaStubEngine: KafkaStubEngine

    @MockkBean
    lateinit var stubLoaderEngine: StubLoaderEngine

    @Autowired
    lateinit var stubCommand: StubCommand

    @BeforeEach
    fun `clean up stub command`() {
        stubCommand.contractPaths = arrayListOf()
    }

    @Test
    fun `when contract files are not given it should load from qontract config`() {
        every { qontractConfig.contractStubPaths() }.returns(arrayListOf("/config/path/to/contract.qontract"))
        every { fileOperations.isFile("/config/path/to/contract.qontract") }.returns(true)
        every { fileOperations.extensionIsNot("/config/path/to/contract.qontract", QONTRACT_EXTENSION) }.returns(false)

        CommandLine(stubCommand, factory).execute()

        verify(exactly = 1) { qontractConfig.contractStubPaths() }
    }

    @Test
    fun `when contract files are given it should not load from qontract config`() {
        every { qontractConfig.contractStubPaths() }.returns(arrayListOf("/config/path/to/contract.qontract"))
        every { fileOperations.isFile("/parameter/path/to/contract.qontract") }.returns(true)
        every { fileOperations.extensionIsNot("/parameter/path/to/contract.qontract", QONTRACT_EXTENSION) }.returns(false)

        CommandLine(stubCommand, factory).execute("/parameter/path/to/contract.qontract")

        verify(exactly = 0) { qontractConfig.contractStubPaths() }
    }

    @Test
    fun `should attempt to start HTTP and Kafka stubs`() {
        val contractPath = "/path/to/contract.qontract"
        val contract = """
            Feature: Math API
              Scenario: Random API
                When GET /
                Then status 200
                And response-body (number)
        """.trimIndent()
        val feature = Feature(contract)

        every { watchMaker.make(listOf(contractPath)) }.returns(watcher)

        val stubInfo = listOf(Pair(feature, emptyList<ScenarioStub>()))
        every { stubLoaderEngine.loadStubs(listOf(contractPath), emptyList()) }.returns(stubInfo)

        val host = "0.0.0.0"
        val port = 9000
        val certInfo = CertInfo()
        val strictMode = false
        val kafkaHost = "localhost"
        val kafkaPort = 9093

        every { httpStubEngine.runHTTPStub(stubInfo, host, port, certInfo, strictMode, any(), any()) }.returns(null)
        every { kafkaStubEngine.runKafkaStub(stubInfo, kafkaHost, kafkaPort, false) }.returns(null)

        every { qontractConfig.contractStubPaths() }.returns(arrayListOf(contractPath))
        every { fileOperations.isFile(contractPath) }.returns(true)
        every { fileOperations.extensionIsNot(contractPath, QONTRACT_EXTENSION) }.returns(false)

        val exitStatus = CommandLine(stubCommand, factory).execute(contractPath)
        assertThat(exitStatus).isZero()

        verify(exactly = 1) { httpStubEngine.runHTTPStub(stubInfo, host, port, certInfo, strictMode, any(), any()) }
        verify(exactly = 1) { kafkaStubEngine.runKafkaStub(stubInfo, kafkaHost, kafkaPort, false) }
    }

    @Test
    fun `when a contract with the correct extension is given it should be loaded`(@TempDir tempDir: Path) {
        val validQontract = tempDir.resolve("contract.qontract")

        val qontractFilePath = validQontract.toAbsolutePath().toString()
        File(qontractFilePath).writeText("""
            Feature: Is a dummy feature
        """.trimIndent())

        every { watchMaker.make(listOf(qontractFilePath)) }.returns(watcher)
        every { qontractConfig.contractStubPaths() }.returns(arrayListOf("/config/path/to/contract.qontract"))
        every { fileOperations.isFile(qontractFilePath) }.returns(true)
        every { fileOperations.extensionIsNot(qontractFilePath, QONTRACT_EXTENSION) }.returns(false)

        val execute = CommandLine(stubCommand, factory).execute(qontractFilePath)

        assertThat(execute).isEqualTo(0)
    }

    @Test
    @ExpectSystemExitWithStatus(1)
    fun `when a contract with the incorrect extension command should exit with non-zero`(@TempDir tempDir: Path) {
        val invalidQontract = tempDir.resolve("contract.contract")

        val qontractFilePath = invalidQontract.toAbsolutePath().toString()
        File(qontractFilePath).writeText("""
            Feature: Is a dummy feature
        """.trimIndent())

        every { watchMaker.make(listOf(qontractFilePath)) }.returns(watcher)
        every { qontractConfig.contractStubPaths() }.returns(arrayListOf("/config/path/to/contract.qontract"))
        every { fileOperations.isFile(qontractFilePath) }.returns(true)
        every { fileOperations.extensionIsNot(qontractFilePath, QONTRACT_EXTENSION) }.returns(true)

        CommandLine(stubCommand, factory).execute(qontractFilePath)
    }

    @Test
    fun `should run the stub with the specified pass-through url target`() {
        val contractPath = "/path/to/contract.qontract"
        val contract = """
            Feature: Simple API
              Scenario: GET request
                When GET /
                Then status 200
        """.trimIndent()

        val feature = Feature(contract)

        every { watchMaker.make(listOf(contractPath)) }.returns(watcher)

        val stubInfo = listOf(Pair(feature, emptyList<ScenarioStub>()))
        every { stubLoaderEngine.loadStubs(listOf(contractPath), emptyList()) }.returns(stubInfo)

        val host = "0.0.0.0"
        val port = 9000
        val certInfo = CertInfo()
        val strictMode = false
        val passThroughTargetBase = "http://passthroughTargetBase"

        every { httpStubEngine.runHTTPStub(stubInfo, host, port, certInfo, strictMode, passThroughTargetBase, any()) }.returns(null)

        every { qontractConfig.contractStubPaths() }.returns(arrayListOf(contractPath))
        every { fileOperations.isFile(contractPath) }.returns(true)
        every { fileOperations.extensionIsNot(contractPath, QONTRACT_EXTENSION) }.returns(false)

        val exitStatus = CommandLine(stubCommand, factory).execute("--passThroughTargetBase=$passThroughTargetBase", contractPath)
        assertThat(exitStatus).isZero()

        verify(exactly = 1) { httpStubEngine.runHTTPStub(stubInfo, host, port, certInfo, strictMode, any(), any()) }
    }
}
