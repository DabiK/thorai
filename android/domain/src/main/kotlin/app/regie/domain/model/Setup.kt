package app.regie.domain.model

/**
 * Les étapes du parcours d'accueil (ticket 28), dans l'ordre. Chacune se vérifie sur l'état réel de
 * la console ([SetupChecks]) ; les facultatives se passent.
 */
enum class SetupStep(val optional: Boolean = false) {
    WELCOME,
    TERMUX,
    BRIDGE,
    VOICE,
    NOTIFICATIONS,
    CONSOLE_MODE(optional = true),
    ANDROID_ACCESS(optional = true),
    AGENTS(optional = true),
    FINISH,
}

/** Une permission que le parcours demande à l'utilisateur. */
enum class SetupPermission { RUN_COMMAND, MICROPHONE, NOTIFICATIONS }

/** Pourquoi le modèle de voix n'a pas pu s'installer. */
enum class VoiceModelFailure { OFFLINE, STORAGE, OTHER }

/** Le modèle de transcription locale (ADR 09). */
sealed interface VoiceModel {
    data object Installed : VoiceModel

    /** La voix choisie n'en a pas besoin (ElevenLabs). */
    data object NotNeeded : VoiceModel

    /** [downloadBytes] restent à télécharger ; [freeBytes] doivent être libres pour l'installer. */
    data class Missing(val downloadBytes: Long, val freeBytes: Long) : VoiceModel

    /** [progress] de 0 à 1 ; `null` pendant le chargement du modèle, après le téléchargement. */
    data class Downloading(val progress: Float?) : VoiceModel

    data class Failed(val failure: VoiceModelFailure) : VoiceModel
}

/** Ce que la console montre de sa préparation. Relu en continu pendant le parcours. */
data class SetupChecks(
    val termux: Boolean = false,
    val runCommand: Boolean = false,
    val bridge: BridgeStatus = BridgeStatus.Connecting,
    val microphone: Boolean = false,
    val voiceModel: VoiceModel = VoiceModel.Missing(0, 0),
    val notifications: Boolean = false,
    val consoleMode: Boolean = false,
    /** `WRITE_SECURE_SETTINGS`, accordée par adb au bout de l'association (ADR 03). */
    val androidAccess: Boolean = false,
    val wirelessDebugging: Boolean = false,
    /** Connexions des agents, `null` tant que le pont ne les a pas données. */
    val agents: List<AgentAuthentication>? = null,
    /** Refusées pour de bon : Android ne les redemande plus, seules les infos de l'appli les accordent. */
    val refused: Set<SetupPermission> = emptySet(),
) {
    fun isDone(step: SetupStep): Boolean = when (step) {
        SetupStep.WELCOME -> true
        // Sans Termux, un pont joint quand même prouve l'installation (banc d'essai, pont du Mac).
        SetupStep.TERMUX -> termux || bridge == BridgeStatus.Connected
        SetupStep.BRIDGE -> bridge == BridgeStatus.Connected
        SetupStep.VOICE -> microphone && (voiceModel == VoiceModel.Installed || voiceModel == VoiceModel.NotNeeded)
        SetupStep.NOTIFICATIONS -> notifications
        SetupStep.CONSOLE_MODE -> consoleMode
        SetupStep.ANDROID_ACCESS -> androidAccess
        SetupStep.AGENTS -> agents.orEmpty().any { it.connected == true }
        SetupStep.FINISH -> false
    }
}

/** Ce qu'un bouton fait sur l'étape affichée. */
enum class SetupAction {
    NEXT, BACK, SKIP, CLOSE,
    OPEN_TERMUX_RELEASES, COPY_INSTALL_COMMANDS,
    GRANT_RUN_COMMAND, RETRY_BRIDGE,
    GRANT_MICROPHONE, DOWNLOAD_VOICE,
    GRANT_NOTIFICATIONS,
    OPEN_ACCESSIBILITY, OPEN_APP_INFO,
    OPEN_DEVELOPER_OPTIONS, COPY_PAIRING_COMMANDS,
    OPEN_AGENTS,
    CREATE_SESSION,
}

/** Les actions de l'étape, dans l'ordre de lecture des boutons : A, B, X, Y. Absentes : bouton muet. */
data class SetupKeys(
    val a: SetupAction? = null,
    val b: SetupAction? = null,
    val x: SetupAction? = null,
    val y: SetupAction? = null,
)

/** Le parcours d'accueil tel que les deux écrans le montrent. */
data class SetupReview(
    val visible: Boolean = false,
    val step: SetupStep = SetupStep.WELCOME,
    val checks: SetupChecks = SetupChecks(),
    /** Les commandes de l'étape viennent d'être copiées : à coller dans Termux. */
    val copied: Boolean = false,
) {
    val done: Boolean get() = checks.isDone(step)
    val position: Int get() = step.ordinal + 1
    val count: Int get() = SetupStep.entries.size

    val keys: SetupKeys get() {
        val back = SetupAction.BACK.takeIf { step != SetupStep.WELCOME }
        val skip = SetupAction.SKIP.takeIf { step.optional && !done }
        return when (step) {
            SetupStep.WELCOME -> SetupKeys(a = SetupAction.NEXT)
            SetupStep.TERMUX -> if (checks.termux || done) {
                SetupKeys(SetupAction.NEXT, back, y = SetupAction.COPY_INSTALL_COMMANDS)
            } else {
                SetupKeys(SetupAction.OPEN_TERMUX_RELEASES, back, y = SetupAction.COPY_INSTALL_COMMANDS)
            }
            SetupStep.BRIDGE -> SetupKeys(
                a = when {
                    done -> SetupAction.NEXT
                    !checks.runCommand -> permission(SetupPermission.RUN_COMMAND, SetupAction.GRANT_RUN_COMMAND)
                    checks.bridge is BridgeStatus.Unreachable -> SetupAction.RETRY_BRIDGE
                    else -> null
                },
                b = back,
            )
            SetupStep.VOICE -> SetupKeys(
                a = when {
                    !checks.microphone -> permission(SetupPermission.MICROPHONE, SetupAction.GRANT_MICROPHONE)
                    // Le téléchargement continue en arrière-plan : on peut avancer sans l'attendre.
                    done || checks.voiceModel is VoiceModel.Downloading -> SetupAction.NEXT
                    else -> SetupAction.DOWNLOAD_VOICE
                },
                b = back,
                // Micro refusé pour de bon ou téléchargement impossible : on avance quand même, sans voix.
                x = SetupAction.SKIP.takeIf { !done && (SetupPermission.MICROPHONE in checks.refused || checks.voiceModel is VoiceModel.Failed) },
            )
            SetupStep.NOTIFICATIONS -> SetupKeys(
                a = if (done) SetupAction.NEXT else permission(SetupPermission.NOTIFICATIONS, SetupAction.GRANT_NOTIFICATIONS),
                b = back,
                x = SetupAction.SKIP.takeIf { !done && SetupPermission.NOTIFICATIONS in checks.refused },
            )
            SetupStep.CONSOLE_MODE -> SetupKeys(
                a = if (done) SetupAction.NEXT else SetupAction.OPEN_ACCESSIBILITY,
                b = back, x = skip,
                // Android peut bloquer le service : « Autoriser les paramètres restreints » est dans les infos.
                y = SetupAction.OPEN_APP_INFO.takeIf { !done },
            )
            SetupStep.ANDROID_ACCESS -> SetupKeys(
                a = if (done) SetupAction.NEXT else SetupAction.OPEN_DEVELOPER_OPTIONS,
                b = back, x = skip,
                y = SetupAction.COPY_PAIRING_COMMANDS.takeIf { !done },
            )
            SetupStep.AGENTS -> SetupKeys(
                a = if (done) SetupAction.NEXT else SetupAction.OPEN_AGENTS.takeIf { checks.bridge == BridgeStatus.Connected },
                b = back, x = skip,
                y = SetupAction.OPEN_AGENTS.takeIf { done },
            )
            SetupStep.FINISH -> SetupKeys(SetupAction.CREATE_SESSION, back, y = SetupAction.CLOSE)
        }
    }

    private fun permission(permission: SetupPermission, grant: SetupAction) =
        if (permission in checks.refused) SetupAction.OPEN_APP_INFO else grant
}

/**
 * Les commandes que le parcours montre et copie : des données, à coller telles quelles dans Termux
 * (`docs/installation.md`, « Sans Mac »). Aucune ne contient de secret.
 */
object SetupCommands {
    const val INSTALL = "pkg update && pkg install git\n" +
        "git clone https://github.com/DabiK/regie.git\n" +
        "cd regie && bash thor/install-on-device.sh"

    /**
     * Entre dans la Debian, demande le port et le code de la fenêtre d'association, associe, puis
     * accorde `WRITE_SECURE_SETTINGS` à l'app. Collées d'un bloc dans Termux, ces lignes ne lancent
     * rien avant la dernière : le port et le code se saisissent ensuite, sans éditer la commande.
     */
    fun pairing(appId: String) = "proot-distro login debian --user dev \\\n" +
        "  --bind \"\$HOME/projects:/home/dev/projects\" \\\n" +
        "  --bind \"\$HOME/.codex:/home/dev/.codex\" \\\n" +
        "  --work-dir /home/dev -- bash -lc '\n" +
        "read -p \"Port : \" p; read -p \"Code : \" c\n" +
        "/opt/thor-android/pair.sh \"\$p\" \"\$c\" &&\n" +
        "adb shell pm grant $appId android.permission.WRITE_SECURE_SETTINGS &&\n" +
        "adb shell settings put global settings_enable_monitor_phantom_procs false'"
}
