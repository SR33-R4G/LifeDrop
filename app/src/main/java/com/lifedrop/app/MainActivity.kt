package com.lifedrop.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.security.MessageDigest
import java.security.SecureRandom

private val Red=Color(0xFFE53955)
private val Ink=Color(0xFF18212F)
private val Muted=Color(0xFF718096)
private val groups=listOf("A+","A-","B+","B-","AB+","AB-","O+","O-")
data class Donor(val id:Long,val name:String,val group:String,val phone:String,val city:String)
data class BloodRequest(val id:Long,val patient:String,val group:String,val units:Int,val hospital:String,val city:String,val phone:String,val urgency:String,val status:String)

class LifeDropDb(context:Context):SQLiteOpenHelper(context,"lifedrop.db",null,2){
 override fun onCreate(db:SQLiteDatabase){
  db.execSQL("CREATE TABLE donors(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT,grp TEXT,phone TEXT UNIQUE,city TEXT,available INTEGER)")
  db.execSQL("CREATE TABLE requests(id INTEGER PRIMARY KEY AUTOINCREMENT,patient TEXT,grp TEXT,units INTEGER,hospital TEXT,city TEXT,phone TEXT,urgency TEXT,status TEXT DEFAULT 'Open')")
  db.execSQL("CREATE TABLE users(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT,email TEXT UNIQUE,salt TEXT,password_hash TEXT)")
 }
 override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int){if(oldVersion<2)db.execSQL("CREATE TABLE IF NOT EXISTS users(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT,email TEXT UNIQUE,salt TEXT,password_hash TEXT)")}
 private fun hash(p:String,s:ByteArray):String{var v=s+p.toByteArray();repeat(12000){v=MessageDigest.getInstance("SHA-256").digest(v)};return Base64.encodeToString(v,Base64.NO_WRAP)}
 fun register(n:String,e:String,p:String):Boolean{val s=ByteArray(16).also{SecureRandom().nextBytes(it)};val v=ContentValues().apply{put("name",n);put("email",e.lowercase());put("salt",Base64.encodeToString(s,Base64.NO_WRAP));put("password_hash",hash(p,s))};return writableDatabase.insert("users",null,v)!=-1L}
 fun login(e:String,p:String):String?=readableDatabase.rawQuery("SELECT name,salt,password_hash FROM users WHERE email=?",arrayOf(e.lowercase())).use{c->if(!c.moveToFirst())null else if(hash(p,Base64.decode(c.getString(1),Base64.NO_WRAP))==c.getString(2))c.getString(0)else null}
 fun saveDonor(n:String,g:String,p:String,c:String,a:Boolean){val v=ContentValues().apply{put("name",n);put("grp",g);put("phone",p);put("city",c);put("available",if(a)1 else 0)};val id=readableDatabase.rawQuery("SELECT id FROM donors WHERE phone=?",arrayOf(p)).use{if(it.moveToFirst())it.getLong(0)else -1L};if(id<0)writableDatabase.insert("donors",null,v)else writableDatabase.update("donors",v,"id=?",arrayOf(id.toString()))}
 fun donors(g:String,c:String):List<Donor> =readableDatabase.rawQuery("SELECT id,name,grp,phone,city FROM donors WHERE available=1 AND (?='All' OR grp=?) AND city LIKE ? ORDER BY name",arrayOf(g,g,"%"+c+"%")).use{cur->buildList{while(cur.moveToNext())add(Donor(cur.getLong(0),cur.getString(1),cur.getString(2),cur.getString(3),cur.getString(4)))}}
 fun addRequest(n:String,g:String,u:Int,h:String,c:String,p:String,urg:String){val v=ContentValues().apply{put("patient",n);put("grp",g);put("units",u);put("hospital",h);put("city",c);put("phone",p);put("urgency",urg);put("status","Open")};writableDatabase.insert("requests",null,v)}
 fun requests():List<BloodRequest> =readableDatabase.rawQuery("SELECT * FROM requests ORDER BY id DESC",null).use{cur->buildList{while(cur.moveToNext())add(BloodRequest(cur.getLong(0),cur.getString(1),cur.getString(2),cur.getInt(3),cur.getString(4),cur.getString(5),cur.getString(6),cur.getString(7),cur.getString(8)))}}
 fun fulfill(id:Long){writableDatabase.execSQL("UPDATE requests SET status='Fulfilled' WHERE id=?",arrayOf(id))}
 fun count(t:String)=readableDatabase.rawQuery("SELECT COUNT(*) FROM "+t,null).use{if(it.moveToFirst())it.getInt(0)else 0}
}
class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{App(LifeDropDb(this))}}}

@Composable private fun App(db:LifeDropDb){
 var page by remember{mutableStateOf("Home")};var refresh by remember{mutableIntStateOf(0)}
 var logged by remember{mutableStateOf(false)};var account by remember{mutableStateOf("")}
 MaterialTheme(colorScheme=lightColorScheme(primary=Red,background=Color(0xFFF6F7FB))){
  Scaffold(topBar={Row(Modifier.fillMaxWidth().background(Color.White).padding(18.dp),verticalAlignment=Alignment.CenterVertically){Text("Life",fontSize=25.sp,fontWeight=FontWeight.Black,color=Ink);Text("Drop",fontSize=25.sp,fontWeight=FontWeight.Black,color=Red);Spacer(Modifier.weight(1f));Text("Blood donor network",fontSize=11.sp,color=Muted)}},
   bottomBar={NavigationBar(containerColor=Color.White){listOf("Home","Requests","Settings").forEach{p->NavigationBarItem(page==p,{page=p},icon={Icon(when(p){"Home"->Icons.Default.Home;"Requests"->Icons.Default.Notifications;else->Icons.Default.Settings},null)},label={Text(p)})}}}
  ){pad->Box(Modifier.fillMaxSize().padding(pad)){when(page){
   "Settings"->Settings(db,logged,account,{account=it;logged=true;refresh++},{logged=false;account="";page="Home"},{"Add donor" .also{page=it}},{"Add request".also{page=it}})
   "Add donor"->if(logged)DonorForm(db){refresh++;page="Settings"}else page="Settings"
   "Add request"->if(logged)RequestForm(db){refresh++;page="Requests"}else page="Settings"
   "Requests"->Requests(db,refresh,logged,{refresh++})
   else->Home(db,refresh)
  }}}
 }
}

@Composable private fun Home(db:LifeDropDb,refresh:Int){
 var group by remember{mutableStateOf("All")};var place by remember{mutableStateOf("")}
 val data=remember(group,place,refresh){db.donors(group,place.trim())};val ctx= LocalContext.current
 Column(Modifier.fillMaxSize().padding(16.dp)){
  Text("Welcome to LifeDrop",color=Muted);Text("Find blood. Save lives.",fontSize=27.sp,color=Ink,fontWeight=FontWeight.Bold)
  Spacer(Modifier.height(10.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Stat("Donors",db.count("donors").toString(),Modifier.weight(1f));Stat("Requests",db.count("requests").toString(),Modifier.weight(1f))}
  Spacer(Modifier.height(12.dp));Text("Search blood donors",fontSize=19.sp,color=Ink,fontWeight=FontWeight.Bold)
  Text("No account required to search.",fontSize=12.sp,color=Muted)
  GroupPicker(group){group=it}
  OutlinedTextField(place,{place=it},label={Text("Location: city, area or district")},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp))
  Text(data.size.toString()+" available donor(s)",color=Muted,modifier=Modifier.padding(vertical=8.dp))
  LazyColumn{items(data,key={it.id}){d->Card(Modifier.fillMaxWidth().padding(vertical=4.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){
   Text(d.group,color=Red,fontSize=20.sp,fontWeight=FontWeight.Bold)
   Column(Modifier.weight(1f).padding(start=9.dp)){Text(d.name,color=Ink,fontWeight=FontWeight.Bold);Text(d.city,color=Muted,fontSize=12.sp)}
   TextButton(onClick={ctx.startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+d.phone)))}){Icon(Icons.Default.Call,null);Text("Call")}
   TextButton(onClick={ctx.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/"+d.phone.filter{it.isDigit()})))}){Icon(Icons.Default.Chat,null);Text("WhatsApp")}
  }}}}
 }
}
@Composable private fun Stat(label:String,value:String,modifier:Modifier){Card(modifier,colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(14.dp)){Text(value,color=Red,fontSize=24.sp,fontWeight=FontWeight.Bold);Text(label,color=Muted)}}}

@Composable private fun Settings(db:LifeDropDb,logged:Boolean,account:String,onLogin:(String)->Unit,onLogout:()->Unit,onDonor:()->Unit,onRequest:()->Unit){
 var mode by remember{mutableStateOf("Login")};var name by remember{mutableStateOf("")};var email by remember{mutableStateOf("")};var pass by remember{mutableStateOf("")};var error by remember{mutableStateOf("")}
 Column(Modifier.fillMaxSize().padding(18.dp)){
  Text("Settings",fontSize=25.sp,color=Ink,fontWeight=FontWeight.Bold)
  if(logged){Text("Signed in as "+account,color=Muted);Button(onClick=onDonor,modifier=Modifier.fillMaxWidth()){Text("Add / update donor details")};Button(onClick=onRequest,modifier=Modifier.fillMaxWidth()){Text("Post blood request")};OutlinedButton(onClick=onLogout,modifier=Modifier.fillMaxWidth()){Text("Log out")}}
  else{
   Text("Login or sign up to add data. Searching is open to everyone.",color=Muted)
   Row{FilterChip(mode=="Login",{mode="Login";error=""},label={Text("Login")});Spacer(Modifier.width(8.dp));FilterChip(mode=="Sign up",{mode="Sign up";error=""},label={Text("Sign up")})}
   if(mode=="Sign up")Field("Full name",name){name=it}
   Field("Email address",email){email=it}
   OutlinedTextField(pass,{pass=it},label={Text("Password (minimum 6 characters)")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth().padding(vertical=4.dp))
   Button(onClick={if(mode=="Sign up"){if(name.isBlank())error="Enter your name" else if(db.register(name.trim(),email.trim(),pass)){onLogin(name.trim())}else error="Email already registered on this device"}else{val u=db.login(email.trim(),pass);if(u==null)error="Incorrect email or password" else onLogin(u)}},enabled=email.contains("@")&&pass.length>=6&&(mode=="Login"||name.isNotBlank()),modifier=Modifier.fillMaxWidth()){Text(if(mode=="Login")"Login" else "Create account")}
   if(error.isNotBlank())Text(error,color=Red,fontSize=12.sp)
  }
  Spacer(Modifier.height(18.dp));Text("TESTING MODE: accounts and data are stored only on this device. Nothing is uploaded or synchronized. Local accounts are for testing only, not production security.",color=Muted,fontSize=12.sp)
 }
}

@Composable private fun DonorForm(db:LifeDropDb,done:()->Unit){
 var name by remember{mutableStateOf("")};var group by remember{mutableStateOf("A+")};var phone by remember{mutableStateOf("")};var city by remember{mutableStateOf("")};var available by remember{mutableStateOf(true)}
 Column(Modifier.fillMaxSize().padding(18.dp)){Text("Add donor details",fontSize=24.sp,color=Ink,fontWeight=FontWeight.Bold);Field("Full name",name){name=it};GroupPicker(group){group=it};Field("Phone number",phone){phone=it};Field("City / area / district",city){city=it};Row(verticalAlignment=Alignment.CenterVertically){Text("Available to donate",Modifier.weight(1f));Switch(available,{available=it})};Button(onClick={db.saveDonor(name.trim(),group,phone.trim(),city.trim(),available);done()},enabled=name.isNotBlank()&&phone.filter{it.isDigit()}.length>=10&&city.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("Save donor")}}
}
@Composable private fun RequestForm(db:LifeDropDb,done:()->Unit){
 var patient by remember{mutableStateOf("")};var group by remember{mutableStateOf("A+")};var units by remember{mutableStateOf("1")};var hospital by remember{mutableStateOf("")};var city by remember{mutableStateOf("")};var phone by remember{mutableStateOf("")};var urgency by remember{mutableStateOf("Urgent")}
 Column(Modifier.fillMaxSize().padding(18.dp)){Text("Post blood request",fontSize=23.sp,color=Ink,fontWeight=FontWeight.Bold);Field("Patient name",patient){patient=it};GroupPicker(group){group=it};Field("Units required",units){units=it.filter{c->c.isDigit()}};Field("Hospital / blood bank",hospital){hospital=it};Field("City / area",city){city=it};Field("Contact phone",phone){phone=it};Row{listOf("Urgent","Critical","Within 24h").forEach{FilterChip(urgency==it,{urgency=it},label={Text(it)})}};Button(onClick={db.addRequest(patient.trim(),group,units.toIntOrNull()?:1,hospital.trim(),city.trim(),phone.trim(),urgency);done()},enabled=patient.isNotBlank()&&hospital.isNotBlank()&&city.isNotBlank()&&phone.filter{it.isDigit()}.length>=10&&(units.toIntOrNull()?:0)>0,modifier=Modifier.fillMaxWidth()){Text("Publish request")}}
}
@Composable private fun Requests(db:LifeDropDb,refresh:Int,logged:Boolean,changed:()->Unit){
 val data=remember(refresh){db.requests()};val ctx=LocalContext.current
 Column(Modifier.fillMaxSize().padding(16.dp)){Text("Blood requests",fontSize=24.sp,color=Ink,fontWeight=FontWeight.Bold);LazyColumn{items(data,key={it.id}){r->Card(Modifier.fillMaxWidth().padding(vertical=5.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(13.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text(r.group,color=Red,fontSize=22.sp,fontWeight=FontWeight.Bold);Column(Modifier.weight(1f).padding(start=10.dp)){Text(r.patient,color=Ink,fontWeight=FontWeight.Bold);Text(r.city+" • "+r.urgency,color=Muted,fontSize=12.sp)};Text(r.status,color=Red)};Text(r.units.toString()+" unit(s) • "+r.hospital);Row{TextButton(onClick={ctx.startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+r.phone)))}){Icon(Icons.Default.Call,null);Text("Call")};TextButton(onClick={ctx.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/"+r.phone.filter{it.isDigit()})))}){Icon(Icons.Default.Chat,null);Text("WhatsApp")};Spacer(Modifier.weight(1f));if(logged&&r.status=="Open")TextButton(onClick={db.fulfill(r.id);changed()}){Text("Fulfilled")}}}}}}}
}
@Composable private fun GroupPicker(selected:String,choose:(String)->Unit){groups.chunked(4).forEach{row->Row(horizontalArrangement=Arrangement.spacedBy(3.dp)){row.forEach{FilterChip(selected==it,{choose(it)},label={Text(it)})}}}}
@Composable private fun Field(label:String,value:String,change:(String)->Unit){OutlinedTextField(value,change,label={Text(label)},singleLine=true,modifier=Modifier.fillMaxWidth().padding(vertical=4.dp),shape=RoundedCornerShape(12.dp))}
