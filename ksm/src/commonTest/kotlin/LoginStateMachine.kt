import AppStates.*
import AppEvents.*
import org.example.stateMachine

sealed class AppStates {
    object Uninitialized : AppStates()
    object RequestEula : AppStates()
    object ExitApp : AppStates()
    sealed class Login : AppStates() {
        object CredentialsPrompt : Login()
        object LoginFailed: Login()
//        class Failed(val reason: LoginFailureReason) : Login()
    }
    object GoToMain : AppStates()
}

sealed class LoginFailureReason {
    object InvalidEmail : LoginFailureReason()
    object InvalidPassword : LoginFailureReason()
}

sealed class AppEvents {
    object EulaOutOfDate : AppEvents()
    object EulaAccepted : AppEvents()
    object EulaDenied : AppEvents()
    object LoginSuccess : AppEvents()
    object LoginFailed : AppEvents()
//    data class LoginFailed(val reason: LoginFailureReason) : AppEvents()
}

val appStartStateMachine = stateMachine {

    state<Uninitialized> {
        on<EulaOutOfDate>() transitionTo RequestEula
        on<EulaAccepted>() transitionTo Login.CredentialsPrompt
    }

    state<RequestEula> {
        on<EulaAccepted>() transitionTo Login.CredentialsPrompt
        on<EulaDenied>() transitionTo ExitApp
    }

    state<Login.CredentialsPrompt> {
        on<LoginSuccess>() transitionTo GoToMain
        on<LoginFailed>() transitionTo Login.LoginFailed
//        on<LoginFailed>() transitionTo<Login.Failed:class> { _, event -> Login.Failed(event.reason) }
    }

}