package com.example.alarm_jinxuan.view.addCity

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.alarm_jinxuan.adapter.CityListAdapter
import com.example.alarm_jinxuan.databinding.ActivityAddCityBinding
import com.example.alarm_jinxuan.model.WorldClockEntity
import com.example.alarm_jinxuan.view.worldClock.WorldClockViewModel
import kotlinx.coroutines.launch

class AddCityActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddCityBinding
    private val viewModel: WorldClockViewModel by viewModels()
    private lateinit var adapter: CityListAdapter
    private lateinit var layoutManager: LinearLayoutManager
    private var currentCities: List<WorldClockEntity> = emptyList()

    // 拼音首字母到城市索引的映射
    private val letterToIndexMap = mutableMapOf<String, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityAddCityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupSideIndexBar()
        setupSearch()
        observeViewModel()

        // 初始搜索
        viewModel.searchCities("")
    }

    private fun setupRecyclerView() {
        adapter = CityListAdapter { city ->
            // 在协程中添加城市，等待数据库操作完成
            lifecycleScope.launch {
                try {
                    viewModel.addWorldClock(city)
                    // 添加成功后返回
                    finish()
                } catch (e: Exception) {
                    // 添加失败，显示错误提示
                }
            }
        }

        layoutManager = LinearLayoutManager(this@AddCityActivity)
        binding.recyclerView.apply {
            layoutManager = this@AddCityActivity.layoutManager
            adapter = this@AddCityActivity.adapter
        }
    }

    private fun setupSideIndexBar() {
        binding.sideIndexBar.onLetterSelectedListener = { letter ->
            // 滚动到对应字母的城市
            scrollToLetter(letter)
        }
    }

    private fun scrollToLetter(letter: String) {
        if (currentCities.isEmpty()) return

        // 从映射中获取索引
        val targetIndex = letterToIndexMap[letter]
        if (targetIndex != null && targetIndex >= 0) {
            binding.recyclerView.scrollToPosition(targetIndex)
        }
    }

    private fun setupSearch() {
        // 设置搜索框
        binding.searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.searchCities(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // 设置返回按钮
        binding.arrow.setOnClickListener {
            finish()
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { uiState ->
            // 更新进度条
            binding.progressBar.visibility = if (uiState.isLoading) View.VISIBLE else View.GONE

            // 更新空提示
            binding.emptyText.visibility = if (uiState.availableCities.isEmpty() && !uiState.isLoading) {
                View.VISIBLE
            } else {
                View.GONE
            }

            // 更新列表
            currentCities = uiState.availableCities
            adapter.updateCities(currentCities)

            // 计算拼音首字母到索引的映射
            buildLetterToIndexMap()
        }
    }

    /**
     * 建立拼音首字母到城市索引的映射
     * 使用中文拼音排序规则来确定每个城市属于哪个首字母
     */
    private fun buildLetterToIndexMap() {
        letterToIndexMap.clear()

        if (currentCities.isEmpty()) return

        // 定义首字母列表（按拼音排序）
        val pinyinLetters = listOf("#", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z")

        // 遍历每个拼音首字母
        for (letter in pinyinLetters) {
            // 找到第一个属于该首字母的城市
            var foundIndex: Int? = null
            for (i in currentCities.indices) {
                val cityFirstChar = getPinyinFirstLetter(currentCities[i].cityName)
                if (cityFirstChar == letter) {
                    foundIndex = i
                    break
                }
            }
            if (foundIndex != null) {
                letterToIndexMap[letter] = foundIndex
            }
        }
    }

    /**
     * 获取中文名的拼音首字母
     * 使用字符列表匹配来确定拼音首字母
     */
    private fun getPinyinFirstLetter(chineseName: String): String {
        if (chineseName.isEmpty()) return "#"

        val firstChar = chineseName[0]
        return when {
            // A
            firstChar in listOf('阿', '爱', '安', '按', '暗', '昂', '傲', '奥', '澳', '懊') -> "A"
            // B
            firstChar in listOf('八', '巴', '扒', '吧', '拔', '把', '坝', '爸', '霸', '白', '百', '柏', '摆', '拜', '班', '般', '板', '版', '办', '半', '伴', '邦', '帮', '榜', '傍', '棒', '包', '胞', '剥', '薄', '雹', '保', '堡', '饱', '宝', '抱', '报', '豹', '暴', '爆', '卑', '杯', '悲', '碑', '北', '贝', '背', '被', '倍', '辈', '备', '惫', '奔', '本', '笨', '崩', '绷', '甭', '泵', '蹦', '逼', '鼻', '比', '彼', '笔', '币', '必', '毕', '闭', '避', '边', '编', '鞭', '扁', '便', '变', '遍', '辨', '辩', '彪', '标', '表', '别', '宾', '彬', '斌', '滨', '兵', '冰', '柄', '饼', '并', '病', '拨', '波', '玻', '勃', '博', '搏', '薄', '伯', '驳', '捕', '哺', '补', '不', '布', '步', '部') -> "B"
            // C
            firstChar in '擦'..'错' -> "C"
            // D
            firstChar in '搭'..'躲' -> "D"
            // E
            firstChar in listOf('蛾', '额', '恩', '二', '儿', '耳', '而', '迩', '洱', '饵', '珥', '铒', '鸸', '贰', '咄', '夺', '铎', '朵', '哚', '垛', '躲', '堕', '惰', '跺') -> "E"
            // F
            firstChar in '发'..'否' -> "F"
            // G
            firstChar in '噶'..'过' -> "G"
            // H
            firstChar in '哈'..'祸' -> "H"
            // J
            firstChar in '击'..'开' -> "J"
            // K
            firstChar in '喀'..'扩' -> "K"
            // L
            firstChar in '拉'..'洛' -> "L"
            // M
            firstChar in '妈'..'没' -> "M"
            // N
            firstChar in '拿'..'诺' -> "N"
            // O
            firstChar in listOf('哦', '噢', '欧', '讴', '殴', '偶', '藕', '沤', '瓯') -> "O"
            // P
            firstChar in listOf('怕', '帕', '爬', '拍', '排', '牌', '派', '攀', '盘', '判', '叛', '盼', '庞', '旁', '胖', '抛', '炮', '跑', '泡', '胚', '培', '赔', '佩', '配', '喷', '盆', '抨', '烹', '朋', '捧', '碰', '批', '披', '霹', '皮', '疲', '脾', '匹', '痞', '屁', '譬', '偏', '篇', '片', '骗', '漂', '飘', '瓢', '票', '撇', '瞥', '拼', '频', '贫', '品', '聘', '乒', '坪', '苹', '萍', '平', '评', '凭', '瓶', '萍', '坡', '泼', '颇', '婆', '迫', '破', '魄', '剖', '扑', '铺') -> "P"
            // Q
            firstChar in listOf('七', '期', '其', '奇', '齐', '祈', '岐', '崎', '脐', '旗', '棋', '骐', '骑', '崎', '淇', '萁', '祈', '颀', '启', '起', '岂', '乞', '企', '气', '汽', '弃', '迄', '泣', '契', '砌', '器', '气') -> "Q"
            // R
            firstChar in listOf('然', '燃', '染', '壤', '攘', '让', '饶', '扰', '绕', '惹', '热', '人', '仁', '忍', '韧', '认', '任', '刃', '扔', '仍', '日', '戎', '茸', '蓉', '荣', '融', '冗', '柔', '揉', '肉', '如', '儒', '乳', '汝', '入', '褥', '软', '锐', '瑞', '润', '弱') -> "R"
            // S
            firstChar in '撒'..'锁' -> "S"
            // T
            firstChar in '他'..'妥' -> "T"
            // W
            firstChar in '挖'..'沃' -> "W"
            // X
            firstChar in listOf('西', '希', '昔', '析', '稀', '息', '牺', '悉', '惜', '溪', '烯', '硒', '晰', '熙', '嬉', '习', '席', '袭', '媳', '洗', '喜', '戏', '系', '细', '隙', '瞎', '虾', '匣', '霞', '辖', '暇', '峡', '狭', '下', '厦', '夏', '吓', '掀', '锨', '先', '仙', '纤', '掀', '鲜', '闲', '弦', '贤', '咸', '衔', '嫌', '显', '险', '现', '献', '县', '腺', '馅', '羡', '宪', '陷', '限', '线', '相', '厢', '镶', '香', '箱', '襄', '湘', '乡', '翔', '祥', '详', '想', '响', '享', '项', '巷', '橡', '像', '向', '象', '萧', '硝', '霄', '削', '哮', '嚣', '销', '消', '宵', '淆', '小', '孝', '校', '肖', '啸', '笑', '效', '楔', '些', '歇', '蝎', '鞋', '协', '挟', '携', '邪', '斜', '胁', '谐', '写', '械', '卸', '蟹', '懈', '泄', '泻', '谢', '屑', '薪', '芯', '锌', '欣', '辛', '新', '忻', '心', '信', '衅', '星', '腥', '猩', '兴', "型", '刑', '形', '邢', '行', '省', '醒', '杏', '姓', '性', '凶', '兄', '胸', '匈', '雄', '熊', '休', '修', '羞', '朽', '嗅', '锈', '秀', '袖', '绣', '墟', '戌', '需', '虚', '须', '徐', '许', '蓄', '酗', '叙', '旭', '序', '畜', '恤', '絮', '婿', '绪', '续', '轩', '喧', '宣', '悬', '旋', '玄', '选', "癣", '眩', '绚', '靴', '薛', '学', '穴', '雪', '血') -> "X"
            // Y
            firstChar in '压'..'韵' -> "Y"
            // Z
            firstChar in '匝'..'座' -> "Z"
            else -> "#"
        }
    }

    override fun onResume() {
        super.onResume()
        // 恢复时刷新数据，显示所有可用城市
        viewModel.searchCities(binding.searchEdit.text.toString())
    }
}