import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.example.AppEvents
import org.example.AppEvents.*
import org.example.AppStates
import org.example.AppStates.*
import org.example.LoginFailureReason
import org.example.stateMachine
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AppStartStateMachineTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private fun newMachine(initial: AppStates) =
        stateMachine<AppStates, AppEvents> {
            initialState = initial
            dispatchedOn = testScope.backgroundScope

            state<Uninitialized> {
                on<EulaOutOfDate>() transitionTo RequestEula
                on<EulaAccepted>() transitionTo Login.RequestInput
            }

            state<RequestEula> {
                on<EulaAccepted>() transitionTo Login.RequestInput
                on<EulaDenied>() transitionTo ExitApp
            }

            state<Login> {
                on<LoginSuccess>() transitionTo GoToMain
                on<LoginFailed>() transitionWith { _, event ->
                    Login.Failed(event.reason)
                }
            }
        }

    @Test
    fun `happy path reaches main`() = testScope.runTest {
        val fsm = newMachine(Uninitialized)

        fsm.currentState.test {
            assertEquals(Uninitialized, awaitItem())

            fsm.dispatchEvent(EulaAccepted)
            assertEquals(Login.RequestInput, awaitItem())

            fsm.dispatchEvent(LoginSuccess)
            assertEquals(GoToMain, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }

        testScope.backgroundScope.cancel()
    }


    @Test
    fun `eula denial exits app`() = testScope.runTest {

        val fsm = newMachine(Uninitialized)

        fsm.currentState.test {
            assertEquals(Uninitialized, awaitItem())

            fsm.dispatchEvent(EulaOutOfDate)
            assertEquals(RequestEula, awaitItem())

            fsm.dispatchEvent(EulaDenied)
            assertEquals(ExitApp, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }

        testScope.backgroundScope.cancel()
    }

    @Test
    fun `login failure transitions with reason`() = testScope.runTest {
        val fsm = newMachine(Login.RequestInput)

        fsm.currentState.test {
            assertEquals(Login.RequestInput, awaitItem())

            val reason = LoginFailureReason.InvalidPassword
            fsm.dispatchEvent(LoginFailed(reason))

            val resultState = awaitItem()
            assertTrue(resultState is Login.Failed)
            assertEquals(reason, (resultState as Login.Failed).reason)

            cancelAndIgnoreRemainingEvents()
        }

        testScope.backgroundScope.cancel()
    }


}
