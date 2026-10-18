package com.example.domain

/** Winkelafdelingen in de volgorde waarin je er meestal langsloopt. */
enum class GroceryDepartment(val label: String) {
    GROENTEN_FRUIT("Groenten & fruit"),
    BROOD("Brood & bakkerij"),
    VLEES_VIS("Vlees, vis & vega"),
    ZUIVEL("Zuivel & eieren"),
    DROGE_WAREN("Pasta, rijst & droge waren"),
    KRUIDEN_SAUZEN("Kruiden, sauzen & conserven"),
    DIEPVRIES("Diepvries"),
    DRANKEN("Dranken"),
    OVERIG("Overig")
}

/** Vaste woordenlijst (geen AI): ingrediëntnaam -> afdeling. */
object GroceryDepartments {

    // Volgorde = matchprioriteit (specifiek eerst), niet weergavevolgorde.
    private val rules: List<Pair<GroceryDepartment, List<String>>> = listOf(
        // Uitzonderingen die anders door een kortere regel worden weggekaapt.
        GroceryDepartment.ZUIVEL to listOf("zoute boter", "zoute roomboter"),
        GroceryDepartment.GROENTEN_FRUIT to listOf("watermeloen", "zoete aardappel", "pepertjes"),
        GroceryDepartment.DIEPVRIES to listOf("diepvries", "ijs", "bevroren"),
        GroceryDepartment.KRUIDEN_SAUZEN to listOf(
            "bouillon", "saus", "ketchup", "mayonaise", "mosterd", "azijn", "sojasaus", "ketjap",
            "sambal", "pesto", "tomatenpuree", "tomatenblokjes", "gepelde tomaten", "passata",
            "kokosmelk", "olie", "olijfolie", "zout", "peper", "poeder", "kruiden", "kruid", "kerrie",
            "kaneel", "komijn", "kurkuma", "oregano", "tijm", "rozemarijn", "laurier", "nootmuskaat",
            "gember", "koriander", "kardemom", "paprikapoeder", "chilivlokken", "currypasta", "currypoeder",
            "maïs", "mais", "kikkererwten", "kidneybonen", "bonen in blik", "linzen", "tonijn", "ansjovis",
            "olijven", "kappertjes", "honing", "siroop", "jam", "pindakaas", "hagelslag", "suiker"
        ),
        GroceryDepartment.DRANKEN to listOf(
            "water", "cola", "frisdrank", "sap", "wijn", "bier", "koffie", "thee", "limonade"
        ),
        GroceryDepartment.BROOD to listOf(
            "brood", "boterham", "wrap", "tortilla", "pita", "bagel", "croissant", "stokbrood",
            "ciabatta", "focaccia", "broodje", "hamburgerbroodje", "naan", "toast", "beschuit", "paneermeel"
        ),
        GroceryDepartment.ZUIVEL to listOf(
            "melk", "boter", "room", "kaas", "yoghurt", "yogurt", "kwark", "skyr", "ei", "eieren",
            "eigeel", "eiwit", "mozzarella", "feta", "parmezaan", "mascarpone", "ricotta", "crème fraîche",
            "creme fraiche", "slagroom", "margarine", "halfvolle", "karnemelk", "vla", "cottage"
        ),
        GroceryDepartment.VLEES_VIS to listOf(
            "gehakt", "kip", "kipfilet", "kippendij", "kalkoen", "rund", "varken", "spek", "spekjes",
            "bacon", "ham", "worst", "chorizo", "salami", "filet", "biefstuk", "karbonade", "schnitzel",
            "braadworst", "hamburger", "burger", "vlees", "lam", "zalm", "vis", "garnaal", "garnalen",
            "kabeljauw", "pangasius", "makreel", "haring", "mosselen", "scampi", "tofu", "tempeh",
            "vegetarisch", "quorn", "seitan", "rookvlees", "kipstuckjes", "kipstukjes"
        ),
        GroceryDepartment.GROENTEN_FRUIT to listOf(
            "ui", "uien", "knoflook", "sjalot", "prei", "wortel", "wortelen", "aardappel", "aardappelen",
            "tomaat", "tomaten", "komkommer", "paprika", "courgette", "aubergine", "broccoli", "bloemkool",
            "spinazie", "sla", "ijsbergsla", "rucola", "spruitjes", "kool", "witlof", "venkel", "champignon",
            "champignons", "paddenstoel", "pompoen", "zoete aardappel", "bataat", "radijs", "rode biet", "biet",
            "selderij", "bleekselderij", "asperge", "doperwten", "sperziebonen", "snijbonen", "erwten",
            "peterselie", "basilicum", "bieslook", "dille", "munt", "lente-ui", "lenteui", "bosui",
            "appel", "peer", "banaan", "citroen", "limoen", "sinaasappel", "mandarijn", "druiven",
            "aardbei", "aardbeien", "framboos", "frambozen", "blauwe bessen", "bessen", "mango", "ananas",
            "avocado", "meloen", "kiwi", "perzik", "nectarine", "pruim", "fruit", "groente", "groenten",
            "salade", "mais kolf", "chilipeper", "rode peper", "spitskool", "rodekool", "witte kool", "snoeptomaat"
        ),
        GroceryDepartment.DROGE_WAREN to listOf(
            "pasta", "spaghetti", "penne", "macaroni", "tagliatelle", "lasagne", "fusilli", "rigatoni",
            "rijst", "couscous", "bulgur", "quinoa", "noedels", "mie", "bami", "nasi", "meel", "bloem",
            "havermout", "muesli", "cornflakes", "granola", "cracker", "chips", "noten", "amandelen",
            "walnoten", "cashew", "pinda", "pitten", "zaden", "rozijnen", "bakpoeder", "gist", "chocolade",
            "koekje", "koek", "snoep", "tortillachips", "linzen droog"
        )
    )

    private val tokenSplit = Regex("[^\\p{L}\\p{N}-]+")

    fun departmentFor(rawName: String): GroceryDepartment {
        val name = rawName.trim().lowercase()
        if (name.isEmpty()) return GroceryDepartment.OVERIG
        val tokens = name.split(tokenSplit).filter { it.isNotEmpty() }
        for ((dept, keywords) in rules) {
            for (kw in keywords) {
                val hit = if (kw.contains(' ')) {
                    name.contains(kw)
                } else {
                    tokens.any { t -> t == kw || (kw.length >= 4 && t.contains(kw)) }
                }
                if (hit) return dept
            }
        }
        return GroceryDepartment.OVERIG
    }

    /** Groepeert items per afdeling in winkelvolgorde; lege afdelingen vervallen, interne volgorde blijft. */
    fun <T> group(items: List<T>, nameOf: (T) -> String): List<Pair<GroceryDepartment, List<T>>> {
        val byDept = items.groupBy { departmentFor(nameOf(it)) }
        return GroceryDepartment.values().mapNotNull { d -> byDept[d]?.let { d to it } }
    }
}
