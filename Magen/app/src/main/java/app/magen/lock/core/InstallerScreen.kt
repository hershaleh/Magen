package app.magen.lock.core

/**
 * Decides whether the package-installer window on screen is Android's uninstall dialog (which must stay usable
 * while APK installs are blocked).
 *
 * A single piece of text is never enough, because an app can pick any name and the installer shows that name.
 * The window counts as an uninstall dialog only when at least [REQUIRED_SIGNALS] different kinds of evidence are
 * present (at least one of them from wording, see [isUninstall]), and no install question is on screen:
 *
 * - STRUCTURE: a view id inside the installer that names the uninstall flow (an app cannot choose view ids)
 * - MESSAGE:   the confirmation question ("Do you want to uninstall ...")
 * - TITLE:     a whole-line dialog title such as "Uninstall app"
 * - BUTTON:    a clickable control labelled "Uninstall"
 * - CONFIRM:   the standard OK + Cancel pair of clickable controls (older Android versions label the positive
 *              button "OK" rather than "Uninstall")
 *
 * Each node contributes at most one kind, so one text (for example an app called "Uninstall") can provide at most
 * one signal. If the evidence is not enough the window is NOT treated as an uninstall dialog, which keeps it
 * blocked. Wording differs between vendors, so this has to be confirmed on real devices.
 *
 * Texts are expected normalised (lower-case, single spaces, see Catalog.norm). Pure logic with no Android
 * dependency, so it can be tested on the JVM.
 */
object InstallerScreen {
    const val REQUIRED_SIGNALS = 2

    /** One piece of the window as seen through the accessibility tree. */
    class Node(val text: String, val clickable: Boolean, val viewId: String? = null)

    enum class Signal { STRUCTURE, MESSAGE, TITLE, BUTTON, CONFIRM }

    private val TITLES = setOf("uninstall", "uninstall app", "uninstall this app", "uninstall update", "הסר", "הסרת התקנה")
    private val BUTTONS = setOf("uninstall", "הסר", "הסרה", "הסרת התקנה", "הסרת ההתקנה")
    private val MESSAGE_STARTS = listOf(
        "do you want to uninstall", "are you sure you want to uninstall",
        "האם ברצונך להסיר", "האם להסיר",
    )
    private val INSTALL_QUESTIONS = listOf(
        "do you want to install", "do you want to update",
        "האם ברצונך להתקין", "האם ברצונך לעדכן",
    )
    private val OK_WORDS = setOf("ok", "אישור", "אשר")
    private val CANCEL_WORDS = setOf("cancel", "ביטול")

    private fun clean(s: String) = s.trim().trimEnd('?', '!', '.', ':').trim()

    /** The single kind of evidence one node provides, or null. CONFIRM is derived from two nodes, see [signals]. */
    fun kindOf(node: Node): Signal? {
        if (node.viewId?.lowercase()?.contains("uninstall") == true) return Signal.STRUCTURE
        val t = clean(node.text)
        if (t.isEmpty()) return null
        return when {
            node.clickable && t in BUTTONS -> Signal.BUTTON
            MESSAGE_STARTS.any { t.startsWith(it) } -> Signal.MESSAGE
            t in TITLES -> Signal.TITLE
            else -> null
        }
    }

    fun signals(nodes: List<Node>): Set<Signal> {
        val found = HashSet<Signal>()
        for (n in nodes) kindOf(n)?.let { found.add(it) }
        val clickableTexts = nodes.filter { it.clickable }.map { clean(it.text) }
        if (clickableTexts.any { it in OK_WORDS } && clickableTexts.any { it in CANCEL_WORDS }) found.add(Signal.CONFIRM)
        return found
    }

    /** True when an install or update question is on screen: that can never be the uninstall dialog. */
    fun isInstallPrompt(nodes: List<Node>): Boolean =
        nodes.any { n -> val t = clean(n.text); INSTALL_QUESTIONS.any { t.startsWith(it) } }

    /** Signals that come from wording. At least one is required, so structure plus a generic OK/Cancel pair is never enough. */
    private val WORDING = setOf(Signal.MESSAGE, Signal.TITLE, Signal.BUTTON)

    /** Clickable labels that start an install or an update. Their presence means this is not an uninstall dialog. */
    private val INSTALL_ACTIONS = setOf("install", "update", "התקן", "התקנה", "עדכן", "עדכון")

    fun hasInstallAction(nodes: List<Node>): Boolean = nodes.any { it.clickable && clean(it.text) in INSTALL_ACTIONS }

    /**
     * Exception for Magen's OWN approved uninstall, so a wording this detector does not know cannot lock the
     * person out of removing Magen right after they confirmed it with the code. All of these must hold:
     * the approval window is open, the screen names Magen, there is no install or update question or action, and
     * there is some uninstall wording (a recognised signal or the word itself). It never applies to other apps
     * and never to install or update screens.
     */
    fun isApprovedMagenUninstall(nodes: List<Node>, mentionsMagen: Boolean, approvalActive: Boolean): Boolean {
        if (!approvalActive || !mentionsMagen) return false
        if (isInstallPrompt(nodes) || hasInstallAction(nodes)) return false
        return signals(nodes).isNotEmpty() || nodes.any { n -> n.text.contains("uninstall") || n.text.contains("הסר") }
    }

    fun isUninstall(nodes: List<Node>): Boolean {
        if (isInstallPrompt(nodes)) return false
        val found = signals(nodes)
        return found.size >= REQUIRED_SIGNALS && found.any { it in WORDING }
    }
}
