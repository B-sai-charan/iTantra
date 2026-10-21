package org.itantra.core.audio.diagnostic.benchmark

/**
 * Standardized 20-sentence Hindi benchmark suite for offline neural TTS physical device validation.
 *
 * Covers diverse sentence lengths, tactical emergency SOS alerts, coordinate and telemetry numbers,
 * formal civilian directives, and conversational operational acknowledgements.
 */
object HindiBenchmarkDataset {

    val sentences: List<BenchmarkSentence> = listOf(
        BenchmarkSentence(
            id = 1,
            text = "आपत्ति सूचना: कृपया सहायता तुरंत भेजें। संकेत एस ओ एस प्राप्त हुआ।",
            domain = "Emergency SOS",
            priority = MessagePriority.EMERGENCY
        ),
        BenchmarkSentence(
            id = 2,
            text = "सेना इकाई चार, सेक्टर दो में गश्त पूरी हो चुकी है।",
            domain = "Tactical Patrol",
            priority = MessagePriority.NORMAL
        ),
        BenchmarkSentence(
            id = 3,
            text = "स्थान निर्देशांक 28.6139 उत्तर और 77.2090 पूर्व दर्ज किए गए हैं।",
            domain = "Coordinates & Telemetry",
            priority = MessagePriority.NORMAL
        ),
        BenchmarkSentence(
            id = 4,
            text = "संदेश प्राप्त हुआ, सब ठीक है।",
            domain = "Short Acknowledgement",
            priority = MessagePriority.NORMAL
        ),
        BenchmarkSentence(
            id = 5,
            text = "चेतावनी: नदी का जलस्तर खतरे के निशान से ऊपर है, सभी लोग सुरक्षित स्थान पर जाएं।",
            domain = "Civilian Flood Alert",
            priority = MessagePriority.EMERGENCY
        ),
        BenchmarkSentence(
            id = 6,
            text = "बैटरी स्तर 85 प्रतिशत है और रेडियो सिग्नल मजबूत है।",
            domain = "Telemetry Status",
            priority = MessagePriority.NORMAL
        ),
        BenchmarkSentence(
            id = 7,
            text = "तत्काल एम्बुलेंस और प्राथमिक चिकित्सा दल की आवश्यकता है।",
            domain = "Medical SOS",
            priority = MessagePriority.EMERGENCY
        ),
        BenchmarkSentence(
            id = 8,
            text = "अगले चौबीस घंटों में भारी वर्षा और तेज हवाओं की संभावना है।",
            domain = "Weather Warning",
            priority = MessagePriority.NORMAL
        ),
        BenchmarkSentence(
            id = 9,
            text = "चौकी नंबर पांच पर सभी वाहन सुरक्षित रूप से गुजर चुके हैं।",
            domain = "Checkpoint Report",
            priority = MessagePriority.NORMAL
        ),
        BenchmarkSentence(
            id = 10,
            text = "पुल संख्या बारह पार करने योग्य नहीं है, कृपया वैकल्पिक मार्ग चुनें।",
            domain = "Route Advisory",
            priority = MessagePriority.EMERGENCY
        ),
        BenchmarkSentence(
            id = 11,
            text = "नमस्ते, वायरलेस नेटवर्क का परीक्षण सफल रहा।",
            domain = "Link Verification",
            priority = MessagePriority.NORMAL
        ),
        BenchmarkSentence(
            id = 12,
            text = "अग्नि शमन दल घटनास्थल पर पहुंच चुका है और आग पर काबू पाया जा रहा है।",
            domain = "Fire Incident Report",
            priority = MessagePriority.EMERGENCY
        ),
        BenchmarkSentence(
            id = 13,
            text = "सैनिकों का दूसरा दस्ता उत्तर दिशा की ओर रवाना हो गया है।",
            domain = "Troop Movement",
            priority = MessagePriority.NORMAL
        ),
        BenchmarkSentence(
            id = 14,
            text = "कृपया अपनी वर्तमान स्थिति की पुष्टि करें और सिग्नल की तीव्रता बताएं।",
            domain = "Radio Interrogative",
            priority = MessagePriority.NORMAL
        ),
        BenchmarkSentence(
            id = 15,
            text = "राहत सामग्री का वितरण केंद्र सुबह आठ बजे से खुला रहेगा।",
            domain = "Relief Operations",
            priority = MessagePriority.NORMAL
        ),
        BenchmarkSentence(
            id = 16,
            text = "अलर्ट: पश्चिमी सीमा पर संदिग्ध गतिविधि देखी गई है, सतर्क रहें।",
            domain = "Perimeter Alert",
            priority = MessagePriority.EMERGENCY
        ),
        BenchmarkSentence(
            id = 17,
            text = "पीने के पानी और भोजन के पैकेट हेलीकॉप्टर द्वारा गिराए जा रहे हैं।",
            domain = "Airdrop Logistics",
            priority = MessagePriority.NORMAL
        ),
        BenchmarkSentence(
            id = 18,
            text = "ट्रांसमीटर आवृत्ति को 144.200 मेगाहर्ट्ज पर सेट किया गया है।",
            domain = "Frequency Configuration",
            priority = MessagePriority.NORMAL
        ),
        BenchmarkSentence(
            id = 19,
            text = "खोज एवं बचाव अभियान में दो सौ जवान सक्रिय रूप से जुटे हुए हैं।",
            domain = "Search and Rescue",
            priority = MessagePriority.NORMAL
        ),
        BenchmarkSentence(
            id = 20,
            text = "यह भारतीय बहुभाषी भाषण संश्लेषण प्रणाली का एक पूर्णतः ऑफलाइन परीक्षण संदेश है।",
            domain = "Full System Benchmark",
            priority = MessagePriority.NORMAL
        )
    )
}
