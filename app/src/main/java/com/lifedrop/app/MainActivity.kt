package com.lifedrop.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Red = Color(0xFFE53955)
private val Ink = Color(0xFF18212F)
private val Muted = Color(0xFF718096)
private val groups = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

data class Donor(val id: Long, val name: String, val group: String, val phone: String, val city: String, val available: Boolean)
data class RequestItem(val id: Long, val patient: String, val group: String, val units: Int, val hospital: String, val city: String, val phone: String, val urgency: String, val status: String)

class LifeDropDb(context: Context) : SQLiteOpenHelper(context, "lifedrop.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE donors(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT,grp TEXT,phone TEXT,city TEXT,available INTEGER)")
        db.execSQL("CREATE TABLE requests(id INTEGER PRIMARY KEY AUTOINCREMENT,patient TEXT,grp TEXT,units INTEGER,hospital TEXT,city TEXT,phone TEXT,urgency TEXT,status TEXT DEFAULT 'Open')")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    fun saveDonor(name: String, group: String, phone: String, city: String, available: Boolean) {
        val values = ContentValues().apply {
            put("name", name); put("grp", group); put("phone", phone); put("city", city); put("available", if (available) 1 else 0)
        }
        val old = readableDatabase.rawQuery("SELECT id FROM donors WHERE phone=?", arrayOf(phone)).use { if (it.moveToFirst()) it.getLong(0) else -1L }
        if (old >= 0) writableDatabase.update("donors", values, "id=?", arrayOf(old.toString()))
        else writableDatabase.insert("donors", null, values)
    }
    fun donors(group: String, city: String): List<Donor> {
        val sql = "SELECT * FROM donors WHERE available=1 AND (?='All' OR grp=?) AND city LIKE ? ORDER BY name"
        return readableDatabase.rawQuery(sql, arrayOf(group, group, "%" + city + "%")).use { c ->
            buildList { while (c.moveToNext()) add(Donor(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getInt(5)==1)) }
        }
    }
    fun addRequest(patient: String, group: String, units: Int, hospital: String, city: String, phone: String, urgency: String) {
        val v = ContentValues().apply {
            put("patient", patient); put("grp", group); put("units", units); put("hospital", hospital)
            put("city", city); put("phone", phone); put("urgency", urgency); put("status", "Open")
        }
        writableDatabase.insert("requests", null, v)
    }
    fun requests(): List<RequestItem> = readableDatabase.rawQuery("SELECT * FROM requests ORDER BY id DESC", null).use { c ->
        buildList { while (c.moveToNext()) add(RequestItem(c.getLong(0),c.getString(1),c.getString(2),c.getInt(3),c.getString(4),c.getString(5),c.getString(6),c.getString(7),c.getString(8))) }
    }
    fun closeRequest(id: Long) { writableDatabase.execSQL("UPDATE requests SET status='Fulfilled' WHERE id=?", arrayOf(id)) }
    fun count(table: String): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM " + table, null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = LifeDropDb(this)
        setContent { LifeDropApp(db) }
    }
}

@Composable
private fun LifeDropApp(db: LifeDropDb) {
    var page by remember { mutableStateOf("Home") }
    var refresh by remember { mutableIntStateOf(0) }
    MaterialTheme(colorScheme = lightColorScheme(primary = Red, background = Color(0xFFF6F7FB))) {
        Scaffold(
            topBar = {
                Row(Modifier.fillMaxWidth().background(Color.White).clickable { page = "Home" }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Life", fontSize = 25.sp, fontWeight = FontWeight.Black, color = Ink)
                    Text("Drop", fontSize = 25.sp, fontWeight = FontWeight.Black, color = Red)
                    Spacer(Modifier.weight(1f)); Text("Blood donor network", color = Muted, fontSize = 11.sp)
                }
            },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    listOf("Home", "Search", "Request", "Requests", "Donor").forEach { p ->
                        NavigationBarItem(selected = page == p, onClick = { page = p },
                            icon = { Icon(when(p) { "Home" -> Icons.Default.Home; "Search" -> Icons.Default.Search; "Request" -> Icons.Default.AddCircle; "Requests" -> Icons.Default.Notifications; else -> Icons.Default.Person }, null) },
                            label = { Text(p) })
                    }
                }
            }
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                when (page) {
                    "Search" -> SearchPage(db, refresh)
                    "Donor" -> DonorPage(db) { refresh++; page = "Home" }
                    "Request" -> RequestPage(db) { refresh++; page = "Requests" }
                    "Requests" -> RequestsPage(db, refresh) { refresh++ }
                    else -> HomePage(db, refresh, { page = "Search" }, { page = "Donor" }, { page = "Request" }, { page = "Requests" })
                }
            }
        }
    }
}

@Composable
private fun HomePage(db: LifeDropDb, refresh: Int, search: () -> Unit, donor: () -> Unit, request: () -> Unit, requests: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Welcome to LifeDrop", color = Muted)
        Text("Find blood. Save lives.", fontSize = 27.sp, color = Ink, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Registered donors", db.count("donors").toString(), Modifier.weight(1f))
            Stat("Blood requests", db.count("requests").toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth().clickable { request() }, colors = CardDefaults.cardColors(containerColor = Red), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("NEED BLOOD URGENTLY?", color = Color.White.copy(alpha=.8f), fontWeight = FontWeight.Bold)
                Text("Create an emergency blood request", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("Post request  →", color = Color.White)
            }
        }
        Spacer(Modifier.height(14.dp))
        Action("Search blood donors", "Filter by blood group and city", Icons.Default.Search, search)
        Action("Register as donor", "Add your details and availability", Icons.Default.PersonAdd, donor)
        Action("Emergency requests", "View active blood requirements", Icons.Default.NotificationsActive, requests)
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(15.dp)) { Text(value, color = Red, fontSize = 26.sp, fontWeight = FontWeight.Bold); Text(label, color = Muted, fontSize = 12.sp) }
    }
}
@Composable
private fun Action(title: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, click: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(top=8.dp).clickable { click() }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Red); Column(Modifier.padding(start=12.dp).weight(1f)) { Text(title, color=Ink, fontWeight=FontWeight.SemiBold); Text(sub, color=Muted, fontSize=12.sp) }
            Icon(Icons.Default.ChevronRight, null, tint=Muted)
        }
    }
}
@Composable
private fun SearchPage(db: LifeDropDb, refresh: Int) {
    var group by remember { mutableStateOf("All") }; var city by remember { mutableStateOf("") }
    val donors = remember(group, city, refresh) { db.donors(group, city) }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Find blood donors", fontSize=24.sp, color=Ink, fontWeight=FontWeight.Bold)
        Field("Search city", city) { city=it }
        Row { listOf("All","A+","A-","B+","B-").forEach { FilterChip(group==it,{group=it},label={Text(it)}) } }
        Row { listOf("AB+","AB-","O+","O-").forEach { FilterChip(group==it,{group=it},label={Text(it)}) } }
        Text(donors.size.toString() + " available donors", color=Muted)
        LazyColumn { items(donors) { d ->
            Card(Modifier.fillMaxWidth().padding(vertical=5.dp), colors=CardDefaults.cardColors(containerColor=Color.White)) {
                Row(Modifier.padding(13.dp), verticalAlignment=Alignment.CenterVertically) {
                    Text(d.group, color=Red, fontSize=22.sp, fontWeight=FontWeight.Bold)
                    Column(Modifier.padding(start=12.dp).weight(1f)) { Text(d.name, color=Ink, fontWeight=FontWeight.Bold); Text(d.city, color=Muted, fontSize=12.sp) }
                    IconButton(onClick={context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + d.phone)))}) { Icon(Icons.Default.Call, "Call", tint=Red) }
                }
            }
        } }
    }
}
@Composable
private fun DonorPage(db: LifeDropDb, saved: () -> Unit) {
    var name by remember { mutableStateOf("") }; var group by remember { mutableStateOf("A+") }
    var phone by remember { mutableStateOf("") }; var city by remember { mutableStateOf("") }
    var available by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Donor registration", color=Ink, fontSize=24.sp, fontWeight=FontWeight.Bold)
        Field("Full name",name){name=it}
        Text("Blood group", color=Ink, fontWeight=FontWeight.SemiBold)
        GroupPicker(group){group=it}
        Field("Phone number",phone){phone=it}
        Field("City / area",city){city=it}
        Row(verticalAlignment=Alignment.CenterVertically) { Text("Available to donate", modifier=Modifier.weight(1f)); Switch(available,{available=it}) }
        Button(onClick={db.saveDonor(name.trim(),group,phone.trim(),city.trim(),available);saved()},
            enabled=name.isNotBlank() && phone.filter(Char::isDigit).length>=10 && city.isNotBlank(),
            modifier=Modifier.fillMaxWidth()) { Text("Save donor profile") }
    }
}
@Composable
private fun RequestPage(db: LifeDropDb, saved: () -> Unit) {
    var patient by remember { mutableStateOf("") }; var group by remember { mutableStateOf("A+") }
    var units by remember { mutableStateOf("1") }; var hospital by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }
    var urgency by remember { mutableStateOf("Urgent") }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Emergency blood request", color=Ink, fontSize=23.sp, fontWeight=FontWeight.Bold)
        Field("Patient name",patient){patient=it}
        Text("Blood group", color=Ink, fontWeight=FontWeight.SemiBold)
        GroupPicker(group){group=it}
        Field("Units required",units){units=it.filter(Char::isDigit)}
        Field("Hospital / blood bank",hospital){hospital=it}
        Field("City / area",city){city=it}
        Field("Contact phone",phone){phone=it}
        Text("Urgency", color=Ink, fontWeight=FontWeight.SemiBold)
        Row { listOf("Urgent","Critical","Within 24h").forEach { FilterChip(urgency==it,{urgency=it},label={Text(it)}) } }
        Button(onClick={db.addRequest(patient.trim(),group,units.toIntOrNull()?:1,hospital.trim(),city.trim(),phone.trim(),urgency);saved()},
            enabled=patient.isNotBlank() && hospital.isNotBlank() && city.isNotBlank() && phone.filter(Char::isDigit).length>=10 && (units.toIntOrNull()?:0)>0,
            modifier=Modifier.fillMaxWidth()) { Text("Publish emergency request") }
    }
}
@Composable
private fun RequestsPage(db: LifeDropDb, refresh: Int, changed: () -> Unit) {
    val data = remember(refresh) { db.requests() }; val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Emergency requests", color=Ink, fontSize=24.sp, fontWeight=FontWeight.Bold)
        LazyColumn { items(data) { r ->
            Card(Modifier.fillMaxWidth().padding(vertical=6.dp), colors=CardDefaults.cardColors(containerColor=Color.White)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(r.group, color=Red, fontSize=22.sp, fontWeight=FontWeight.Bold)
                        Column(Modifier.padding(start=10.dp).weight(1f)) { Text(r.patient, color=Ink, fontWeight=FontWeight.Bold); Text(r.city + " • " + r.urgency, color=Muted, fontSize=12.sp) }
                        Text(r.status, color=Red)
                    }
                    Text(r.units.toString() + " unit(s) • " + r.hospital, color=Ink)
                    Row {
                        TextButton(onClick={context.startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+r.phone)))}) { Icon(Icons.Default.Call,null); Text("Contact") }
                        Spacer(Modifier.weight(1f))
                        if(r.status=="Open") TextButton(onClick={db.closeRequest(r.id);changed()}) { Text("Fulfilled") }
                    }
                }
            }
        } }
    }
}
@Composable
private fun GroupPicker(selected: String, choose: (String)->Unit) {
    groups.chunked(4).forEach { row -> Row { row.forEach { FilterChip(selected==it,{choose(it)},label={Text(it)}) } } }
}
@Composable
private fun Field(label: String, value: String, change: (String)->Unit) {
    OutlinedTextField(value,change,label={Text(label)},singleLine=true,
        modifier=Modifier.fillMaxWidth().padding(vertical=4.dp),shape=RoundedCornerShape(12.dp))
}
