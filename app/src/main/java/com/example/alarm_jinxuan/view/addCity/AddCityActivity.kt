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
        if (targetIndex != null && targetIndex >= 0 && targetIndex < currentCities.size) {
            binding.recyclerView.scrollToPosition(targetIndex)
        }
    }

    private fun setupSearch() {
        // 设置搜索框
        binding.searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.searchCities(s.toString())
                // 搜索时立即更新字母索引的可见性
                if (s.toString().isNotEmpty()) {
                    binding.sideIndexBar.visibility = View.GONE
                }
            }

            override fun afterTextChanged(s: Editable?) {
                // 清空时，字母索引会在observeViewModel中重新显示
            }
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

            // 判断是否在搜索状态
            val isSearching = binding.searchEdit.text.toString().isNotEmpty()

            // 计算拼音首字母到索引的映射
            if (isSearching) {
                // 搜索时隐藏字母索引
                binding.sideIndexBar.visibility = View.GONE
            } else {
                // 非搜索时显示字母索引
                binding.sideIndexBar.visibility = View.VISIBLE
                buildLetterToIndexMap()
            }
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

        // 显示所有字母
        binding.sideIndexBar.updateLetters(pinyinLetters)
    }

    /**
     * 获取中文名的拼音首字母
     * 使用Collator来比较字符和参考字符
     */
    private fun getPinyinFirstLetter(chineseName: String): String {
        if (chineseName.isEmpty()) return "#"

        val firstChar = chineseName[0]

        // 使用Collator来比较字符，因为中文字符的Unicode排序和拼音排序不一致
        val collator = java.text.Collator.getInstance(java.util.Locale.CHINA)

        // 定义每个拼音字母的参考字符（这个字符是该字母的开头）
        val letterBoundaries = listOf(
            Pair("A", '阿'),
            Pair("B", '八'),
            Pair("C", '擦'),
            Pair("D", '搭'),
            Pair("E", '蛾'),
            Pair("F", '发'),
            Pair("G", '噶'),
            Pair("H", '哈'),
            Pair("J", '击'),
            Pair("K", '喀'),
            Pair("L", '拉'),
            Pair("M", '妈'),
            Pair("N", '拿'),
            Pair("O", '哦'),
            Pair("P", '怕'),
            Pair("Q", '七'),
            Pair("R", '然'),
            Pair("S", '撒'),
            Pair("T", '他'),
            Pair("W", '挖'),
            Pair("X", '西'),
            Pair("Y", '压'),
            Pair("Z", '匝'),
            Pair("#", '座') // 用'座'作为结尾参考字符，因为'座'是拼音z开头中最大的常见字
        )

        // 从前往后比较，找到第一个比firstChar大的参考字符
        for (i in letterBoundaries.indices) {
            if (collator.compare(firstChar.toString(), letterBoundaries[i].second.toString()) < 0) {
                return letterBoundaries[i].first
            }
        }

        // 如果比所有参考字符都大，返回"#"
        return "#"
    }

    override fun onResume() {
        super.onResume()
        // 恢复时刷新数据，显示所有可用城市
        viewModel.searchCities(binding.searchEdit.text.toString())
    }
}