package com.example.game.data

enum class WordCategory(val arabicTitle: String, val iconName: String) {
    FOOD("الأكل", "restaurant"),
    HOME("المنزل", "home"),
    TOOLS("الأدوات", "build"),
    COUNTRIES("الدول", "public"),
    RANDOM("عشوائي", "casino");

    companion object {
        fun fromKey(key: String): WordCategory {
            return entries.firstOrNull { it.name == key } ?: RANDOM
        }
    }
}

data class SecretWordSelection(
    val effectiveCategory: WordCategory,
    val secretWord: String,
    val spyOptions: List<String> // Always 8 unique words: 1 correct + 7 category distractors
)

object ArabicWordBank {

    val foodWords: List<String> = listOf(
        "شاورما", "كبسة", "فلافل", "منسف", "كشري", "ملوخية", "مسخن", "مقلوبة",
        "كنافة", "بقلاوة", "سمبوسة", "حمص", "تبولة", "فتوش", "ورق عنب", "محشي",
        "بيتزا", "برجر", "سوشي", "معكرونة", "لازانيا", "ستيك", "شيش طاووق", "كباب",
        "مندي", "حنيذ", "مظبي", "جريش", "قرصان", "مرقوق", "سليق", "مطبق",
        "معصوب", "عريكة", "لقيمات", "قطايف", "أم علي", "بسبوسة", "مهلبية", "أرز بالحليب",
        "عدس", "فول مدمس", "شكشوكة", "بيض مقلي", "جبنة حلوم", "لبنة", "زعتر", "زيتون",
        "تفاح", "موز", "برتقال", "مانجو", "فراولة", "بطيخ", "شمام", "عنب",
        "رمان", "تين", "تمر", "أناناس", "كيوي", "خوخ", "مشمش", "كرز"
    ).distinct()

    val homeWords: List<String> = listOf(
        "أريكة", "سرير", "خزانة ملابس", "ثلاجة", "غسالة", "مكيف هواء", "تلفاز", "سجادة",
        "ستارة", "مرآة", "طاولة طعام", "مكتب", "كرسي", "وسادة", "بطانية", "مصباح",
        "ثريا", "مروحة سقف", "فرن", "ميكروويف", "غلاية ماء", "خلاط", "مكنسة كهربائية", "مكواة",
        "رف كتب", "ساعة حائط", "لوحة فنية", "مزهرية", "مغسلة", "دش استحمام", "منشفة", "صابون",
        "شرفة", "نافذة", "باب خشبي", "مفتاح", "جرس الباب", "درج", "مصعد", "حديقة منزلية",
        "مطبخ", "غرفة نوم", "صالة جلوس", "حمام", "قبو", "سطح المنزل", "مدفأة", "طفاية حريق",
        "سلة مهملات", "ممسحة", "نبتة داخلية", "حوض سمك", "مكتبة تلفاز", "طاولة قهوة", "خزانة أحذية", "شماعة ملابس"
    ).distinct()

    val toolsWords: List<String> = listOf(
        "مطرقة", "مفك براغي", "كماشة", "منشار", "مثقاب كهربائي", "مفتاح ربط", "متر قياس", "ميزان ماء",
        "مقص", "سكين متعدد", "فأس", "مجرفة", "معول", "عربة يدوية", "سلم معدني", "حبل متين",
        "مصباح يدوي", "عدسة مكبرة", "بوصلة", "ملقط", "إبرة خياطة", "دباسة", "شريط لاصق", "غراء حراري",
        "فرشاة دهان", "أسطوانة طلاء", "ورق صنفرة", "قفل حديدي", "سلسلة معدنية", "مسمار", "برغي", "صامولة",
        "مبرد حديد", "مفتاح سداسي", "قطاعة أسلاك", "جهاز قياس كهرباء", "كاوية لحام", "قلم رصاص نجار", "طباشير", "مشبك تثبيت",
        "رافعة سيارة", "مضخة هواء", "خرطوم مياه", "بخاخ ماء", "مكنسة يدوية", "قفازات عمل", "نظارة واقية", "خوذة أمان",
        "سماعة طبيب", "محرار", "تلسكوب", "منظار ثنائي", "آلة حاسبة", "ساعة إيقاف", "ميكروفون", "كاميرا تصوير"
    ).distinct()

    val countriesWords: List<String> = listOf(
        "السعودية", "مصر", "الإمارات", "المغرب", "الأردن", "الكويت", "قطر", "عُمان",
        "البحرين", "العراق", "لبنان", "فلسطين", "سوريا", "الجزائر", "تونس", "ليبيا",
        "السودان", "اليمن", "موريتانيا", "تركيا", "اليابان", "كوريا الجنوبية", "الصين", "الهند",
        "إندونيسيا", "ماليزيا", "باكستان", "فرنسا", "إيطاليا", "إسبانيا", "ألمانيا", "بريطانيا",
        "سويسرا", "النرويج", "السويد", "اليونان", "البرتغال", "هولندا", "روسيا", "كندا",
        "الولايات المتحدة", "المكسيك", "البرازيل", "الأرجنتين", "تشيلي", "كولومبيا", "أستراليا", "نيوزيلندا",
        "جنوب أفريقيا", "نيجيريا", "كينيا", "السنغال"
    ).distinct()

    fun getWordsForCategory(category: WordCategory): List<String> {
        return when (category) {
            WordCategory.FOOD -> foodWords
            WordCategory.HOME -> homeWords
            WordCategory.TOOLS -> toolsWords
            WordCategory.COUNTRIES -> countriesWords
            WordCategory.RANDOM -> foodWords + homeWords + toolsWords + countriesWords
        }
    }

    /**
     * Picks a secret word and 7 plausible distractors from the SAME category so the answer
     * is never unfairly obvious, avoiding previously used words in the current match when possible.
     */
    fun selectSecretWordAndOptions(
        requestedCategory: WordCategory,
        usedWords: Set<String> = emptySet(),
        random: kotlin.random.Random = kotlin.random.Random.Default
    ): SecretWordSelection {
        val concreteCategories = listOf(
            WordCategory.FOOD,
            WordCategory.HOME,
            WordCategory.TOOLS,
            WordCategory.COUNTRIES
        )
        val effectiveCategory = if (requestedCategory == WordCategory.RANDOM) {
            concreteCategories[random.nextInt(concreteCategories.size)]
        } else {
            requestedCategory
        }

        val allCategoryWords = getWordsForCategory(effectiveCategory).distinct()
        val availableUnused = allCategoryWords.filter { it !in usedWords }
        val candidatePool = if (availableUnused.isNotEmpty()) availableUnused else allCategoryWords

        val secretWord = candidatePool[random.nextInt(candidatePool.size)]
        val distractors = allCategoryWords
            .filter { it != secretWord }
            .shuffled(random)
            .take(7)

        val options = (distractors + secretWord).distinct().shuffled(random)
        return SecretWordSelection(
            effectiveCategory = effectiveCategory,
            secretWord = secretWord,
            spyOptions = options
        )
    }
}
