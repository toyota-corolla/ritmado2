package com.ritmo.planner

import android.app.Activity
import android.os.Bundle
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.text.InputType
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar

private const val BG = 0xFFE5FAF7.toInt()
private const val INK = 0xFF16465A.toInt()
private const val MUTED = 0xFF54818B.toInt()
private const val AQUA = 0xFF28B8C6.toInt()

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("ritmo_data", MODE_PRIVATE) }
    private lateinit var root: LinearLayout
    private var page = "home"
    private val date get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private val prettyDate get() = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date())

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); showPage("home") }

    private fun showPage(target: String) {
        page = target
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG) }
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 18, 20, 18) }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(6,8,6,8); setBackgroundColor(0xFFD2F1EF.toInt()) }
        listOf("home" to "INÍCIO", "money" to "GANHOS", "body" to "FÍSICO", "social" to "SOCIAL").forEach { (key,label) ->
            val b = Button(this).apply { text = label; textSize = 10f; isAllCaps = false; setTextColor(if (page==key) 0xFFFFFFFF.toInt() else INK); background = pill(if(page==key) AQUA else 0xFFE9FFFF.toInt()); setOnClickListener { showPage(key) } }
            nav.addView(b, LinearLayout.LayoutParams(0, 48, 1f).apply { setMargins(3,0,3,0) })
        }
        root.addView(nav)
        setContentView(root)
        when(target) { "money" -> moneyPage(body); "body" -> bodyPage(body); "social" -> socialPage(body); else -> homePage(body) }
    }

    private fun homePage(b: LinearLayout) {
        b.addView(label("RITMO  /  LIFE SYSTEM", 12, MUTED, true))
        b.addView(label("Seu progresso,\nem movimento.", 30, INK, true))
        b.addView(label("PAINEL PESSOAL  •  $prettyDate", 11, MUTED, true))
        val income = todayIncome()
        val exercises = todayExercise()
        val entries = readArray("social")
        card(b) {
            addView(label("GANHOS DE HOJE", 12, MUTED, true))
            addView(label("R$ ${money(income)}", 30, INK, true))
            addView(label("${readArray("income").length()} lançamento(s) registrado(s)", 12, MUTED))
            addView(label("EVOLUÇÃO DOS ÚLTIMOS 7 DIAS", 11, MUTED, true))
            addView(IncomeChart(this@MainActivity, lastSevenDaysIncome()).apply {
                layoutParams = LinearLayout.LayoutParams(-1, 190).apply { setMargins(0, 4, 0, 0) }
            })
            addView(label("Toque em Ganhos para registrar uma receita e atualizar este gráfico.", 11, MUTED))
        }
        card(b) {
            addView(label("MOVIMENTO DE HOJE", 12, MUTED, true))
            addView(label("${exercises.first} flexões  •  ${exercises.second} barras", 21, INK, true))
            addView(label("Registre cada treino para acompanhar sua evolução.", 12, MUTED))
        }
        card(b) {
            addView(label("PROJETO SOCIAL", 12, MUTED, true))
            addView(label("${entries.length()} avanços documentados", 22, INK, true))
            addView(label("Ideias, entregas, reuniões e próximos passos.", 12, MUTED))
        }
        b.addView(label("ATALHOS", 12, MUTED, true))
        action(b, "＋  Registrar ganhos") { showPage("money") }
        action(b, "＋  Registrar treino") { showPage("body") }
        action(b, "＋  Registrar avanço social") { showPage("social") }
    }

    private fun moneyPage(b: LinearLayout) {
        header(b,"01 / FINANCEIRO","Ganhos diários")
        val total = todayIncome()
        card(b) { addView(label("TOTAL DE HOJE",12,MUTED,true)); addView(label("R$ ${money(total)}",32,INK,true)) }
        val value = field("Valor recebido (ex.: 150,00)", true)
        val desc = field("Origem / descrição", false)
        b.addView(value)
        b.addView(desc)
        action(b,"SALVAR RECEITA") {
            val amount = value.text.toString().trim().replace(".","").replace(",",".").toDoubleOrNull()
            if (amount == null || amount <= 0) toast("Digite um valor válido") else {
                val a = readArray("income"); a.put(JSONObject().put("date",date).put("amount",amount).put("desc",desc.text.toString().ifBlank { "Receita" }))
                saveArray("income",a); IncomeWidgetProvider.refreshAll(this@MainActivity); showPage("money"); toast("Receita salva")
            }
        }
        b.addView(label("HISTÓRICO",13,MUTED,true))
        val arr = readArray("income")
        for (i in arr.length()-1 downTo 0) { val o=arr.getJSONObject(i); rowCard(b,"${o.optString("desc")}  •  ${o.optString("date")}","R$ ${money(o.optDouble("amount"))}") }
        if(arr.length()==0) b.addView(label("Nenhuma receita registrada ainda.",13,MUTED))
    }

    private fun bodyPage(b: LinearLayout) {
        header(b,"02 / PERFORMANCE","Treino físico")
        val totals = allExerciseTotals()
        card(b) { addView(label("ACUMULADO",12,MUTED,true)); addView(label("${totals.first} flexões  •  ${totals.second} barras",22,INK,true)) }
        val push = field("Quantidade de flexões",true); val pull = field("Quantidade de barras",true); val note=field("Observação (opcional)",false)
        b.addView(push); b.addView(pull); b.addView(note)
        action(b,"SALVAR TREINO") {
            val p=push.text.toString().toIntOrNull() ?: 0; val q=pull.text.toString().toIntOrNull() ?: 0
            if(p<0||q<0||(p==0&&q==0)) toast("Informe pelo menos uma quantidade válida") else {
                val a=readArray("workouts"); a.put(JSONObject().put("date",date).put("push",p).put("pull",q).put("note",note.text.toString()))
                saveArray("workouts",a); showPage("body"); toast("Treino salvo")
            }
        }
        b.addView(label("EVOLUÇÃO — ÚLTIMOS REGISTROS",13,MUTED,true))
        val arr=readArray("workouts"); val recent=mutableListOf<Triple<String,Int,Int>>()
        for(i in maxOf(0,arr.length()-7) until arr.length()) { val o=arr.getJSONObject(i); recent.add(Triple(o.optString("date").takeLast(5),o.optInt("push"),o.optInt("pull"))) }
        b.addView(ProgressChart(this,recent).apply { layoutParams=LinearLayout.LayoutParams(-1,210).apply { setMargins(0,8,0,12) } })
        for(i in arr.length()-1 downTo 0) { val o=arr.getJSONObject(i); rowCard(b,o.optString("date"),"${o.optInt("push")} flexões  •  ${o.optInt("pull")} barras") }
        if(arr.length()==0) b.addView(label("Seu gráfico aparecerá após o primeiro treino.",13,MUTED))
    }

    private fun socialPage(b: LinearLayout) {
        header(b,"03 / IMPACTO","Projeto social")
        card(b) { addView(label("DIÁRIO DE EVOLUÇÃO",12,MUTED,true)); addView(label("Registre o que saiu do papel.",20,INK,true)); addView(label("Documente entregas, contatos, reuniões, resultados e próximos passos.",12,MUTED)) }
        val title=field("Título do avanço",false); val detail=field("O que aconteceu? Qual o próximo passo?",false, true)
        b.addView(title); b.addView(detail)
        action(b,"REGISTRAR AVANÇO") {
            if(title.text.isNullOrBlank() || detail.text.isNullOrBlank()) toast("Preencha título e descrição") else {
                val a=readArray("social"); a.put(JSONObject().put("date",date).put("title",title.text.toString()).put("detail",detail.text.toString()))
                saveArray("social",a); showPage("social"); toast("Avanço registrado")
            }
        }
        b.addView(label("LINHA DO TEMPO",13,MUTED,true))
        val arr=readArray("social")
        for(i in arr.length()-1 downTo 0) { val o=arr.getJSONObject(i); card(b) { addView(label(o.optString("date"),11,MUTED,true)); addView(label(o.optString("title"),18,INK,true)); addView(label(o.optString("detail"),13,MUTED)) } }
        if(arr.length()==0) b.addView(label("Nenhum avanço registrado. Comece pelo primeiro.",13,MUTED))
    }

    private fun header(b:LinearLayout,tag:String,title:String){ b.addView(label(tag,12,MUTED,true)); b.addView(label(title,29,INK,true)) }
    private fun label(s:String,size:Int,color:Int,bold:Boolean=false)=TextView(this).apply { text=s; textSize=size.toFloat(); setTextColor(color); if(bold)setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(0,5,0,7) }
    private fun pill(color:Int)=android.graphics.drawable.GradientDrawable().apply { setColor(color); cornerRadius=22f }
    private fun card(parent:LinearLayout, content:LinearLayout.()->Unit) { val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(16,13,16,13); background=android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,intArrayOf(0xFFFFFFFF.toInt(),0xFFD8F5F4.toInt())).apply { cornerRadius=22f; setStroke(1,0xFFB5E6E3.toInt()) }; content() }; parent.addView(box,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,10,0,10)}) }
    private fun field(hint:String,numeric:Boolean,multi:Boolean=false)=EditText(this).apply { this.hint=hint; textSize=14f; setTextColor(INK); setHintTextColor(MUTED); inputType=if(numeric) (InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL) else (InputType.TYPE_CLASS_TEXT or (if(multi) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0)); if(multi){minLines=3;gravity=Gravity.TOP}; setPadding(14,12,14,12); background=pill(0xFFFFFFFF.toInt()); layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,6,0,6)}.also{ } }
    private fun action(parent:LinearLayout,text:String,onClick:()->Unit){ val btn=Button(this).apply{this.text=text; textSize=13f; setTextColor(Color.WHITE); background=pill(AQUA); setOnClickListener{onClick()} }; parent.addView(btn,LinearLayout.LayoutParams(-1,52).apply{setMargins(0,6,0,6)}) }
    private fun rowCard(parent:LinearLayout,left:String,right:String){ card(parent){ val r=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}; r.addView(label(left,13,INK,true),LinearLayout.LayoutParams(0,-2,1f)); r.addView(label(right,14,AQUA,true)); addView(r) } }
    private fun readArray(key:String)=try{JSONArray(prefs.getString(key,"[]"))}catch(_:Exception){JSONArray()}
    private fun saveArray(key:String,a:JSONArray){prefs.edit().putString(key,a.toString()).apply()}
    private fun todayIncome():Double { val a=readArray("income"); var s=0.0; for(i in 0 until a.length()){val o=a.getJSONObject(i);if(o.optString("date")==date)s+=o.optDouble("amount")};return s }
    private fun todayExercise():Pair<Int,Int>{val a=readArray("workouts");var p=0;var q=0;for(i in 0 until a.length()){val o=a.getJSONObject(i);if(o.optString("date")==date){p+=o.optInt("push");q+=o.optInt("pull")}};return p to q}
    private fun allExerciseTotals():Pair<Int,Int>{val a=readArray("workouts");var p=0;var q=0;for(i in 0 until a.length()){p+=a.getJSONObject(i).optInt("push");q+=a.getJSONObject(i).optInt("pull")};return p to q}
    private fun lastSevenDaysIncome(): List<Pair<String, Double>> {
        val totals = linkedMapOf<String, Double>()
        val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val labelFormat = SimpleDateFormat("dd/MM", Locale("pt", "BR"))
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -6)
        repeat(7) {
            val key = keyFormat.format(calendar.time)
            totals[key] = 0.0
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        val entries = readArray("income")
        for (i in 0 until entries.length()) {
            val item = entries.optJSONObject(i) ?: continue
            val key = item.optString("date")
            if (totals.containsKey(key)) totals[key] = (totals[key] ?: 0.0) + item.optDouble("amount")
        }
        return totals.map { (key, amount) ->
            val parsed = try { keyFormat.parse(key) } catch (_: Exception) { null }
            (if (parsed != null) labelFormat.format(parsed) else key.takeLast(5)) to amount
        }
    }

    private fun money(v:Double)=String.format(Locale("pt","BR"),"%,.2f",v)
    private fun toast(s:String){Toast.makeText(this,s,Toast.LENGTH_SHORT).show()}
}

private class ProgressChart(context: android.content.Context, private val points:List<Triple<String,Int,Int>>):View(context){
    private val paint=Paint(3)
    override fun onDraw(canvas:Canvas){
        super.onDraw(canvas); val w=width.toFloat(); val h=height.toFloat(); val left=30f; val right=w-12f; val top=20f; val bottom=h-34f
        paint.color=0xFFB7E3E1.toInt(); paint.strokeWidth=1f
        for(i in 0..3){val y=top+i*(bottom-top)/3f;canvas.drawLine(left,y,right,y,paint)}
        paint.textSize=22f; paint.color=0xFF16899B.toInt(); canvas.drawText("— Flexões",left,14f,paint); paint.color=0xFF55B78E.toInt(); canvas.drawText("— Barras",left+125f,14f,paint)
        if(points.isEmpty())return
        val max=points.maxOf{maxOf(it.second,it.third)}.coerceAtLeast(1).toFloat(); val slot=(right-left)/(points.size.coerceAtLeast(1))
        fun drawSeries(value:(Triple<String,Int,Int>)->Int,color:Int){
            paint.color=color;paint.strokeWidth=5f;paint.style=Paint.Style.STROKE;paint.strokeCap=Paint.Cap.ROUND
            val path=Path();points.forEachIndexed{idx,p->val x=left+slot*(idx+.5f);val y=bottom-(value(p)/max)*(bottom-top);if(idx==0)path.moveTo(x,y)else path.lineTo(x,y);paint.style=Paint.Style.FILL;canvas.drawCircle(x,y,5f,paint);paint.style=Paint.Style.STROKE};canvas.drawPath(path,paint);paint.style=Paint.Style.FILL
        }
        drawSeries({it.second},0xFF16899B.toInt());drawSeries({it.third},0xFF55B78E.toInt())
        paint.color=0xFF54818B.toInt();paint.textSize=21f;points.forEachIndexed{idx,p->canvas.drawText(p.first,left+slot*(idx+.5f)-18f,h-6f,paint)}
    }
}


private class IncomeChart(context: android.content.Context, private val points: List<Pair<String, Double>>) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        val left = 12f; val right = w - 8f; val top = 18f; val bottom = h - 34f
        val maxValue = points.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0
        paint.color = 0xFFB7E3E1.toInt(); paint.strokeWidth = 1f
        for (i in 0..3) {
            val y = top + i * (bottom - top) / 3f
            canvas.drawLine(left, y, right, y, paint)
        }
        if (points.isEmpty()) return
        val slot = (right - left) / points.size
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = left + slot * (index + 0.5f)
            val y = bottom - ((point.second / maxValue).toFloat() * (bottom - top))
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            paint.color = 0xFF16899B.toInt(); paint.style = Paint.Style.FILL
            canvas.drawCircle(x, y, 4.5f, paint)
            paint.color = 0xFF54818B.toInt(); paint.textSize = 10f
            val label = point.first
            canvas.drawText(label, x - paint.measureText(label) / 2f, h - 8f, paint)
        }
        paint.color = 0xFF16899B.toInt(); paint.strokeWidth = 3.5f
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        if (points.all { it.second == 0.0 }) {
            paint.color = 0xFF54818B.toInt(); paint.textSize = 12f
            canvas.drawText("Registre ganhos para visualizar sua evolução", left, (top + bottom) / 2f, paint)
        }
    }
}
