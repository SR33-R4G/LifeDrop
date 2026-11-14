package com.lifedrop.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Red=Color(0xFFE53955)
private val Ink=Color(0xFF18212F)
private val Muted=Color(0xFF718096)
private val groups=listOf("A+","A-","B+","B-","AB+","AB-","O+","O-")

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{LifeDropApp()}}
}

@Composable private fun LifeDropApp(){
    val context= LocalContext.current
    val scope= rememberCoroutineScope()
    var page by remember{mutableStateOf("Home")}
    var session by remember{mutableStateOf(SupabaseClient.loadSession(context))}
    var donors by remember{mutableStateOf<List<Donor>>(emptyList())}
    var requests by remember{mutableStateOf<List<BloodRequest>>(emptyList())}
    var group by remember{mutableStateOf("All")}
    var place by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)}
    var notice by remember{mutableStateOf("")}
    var refresh by remember{mutableIntStateOf(0)}
    var usernameSuggestions by remember{mutableStateOf<List<String>>(emptyList())}

    fun loadData(){
        scope.launch {
            busy=true
            try {
                val result=withContext(Dispatchers.IO){Pair(SupabaseClient.donors(group,place.trim()),SupabaseClient.requests())}
                donors=result.first;requests=result.second;notice=""
            } catch(e:Exception){notice=e.message ?: "Unable to load cloud data"}
            busy=false
        }
    }
    LaunchedEffect(group,place,refresh){loadData()}

    MaterialTheme(colorScheme=lightColorScheme(primary=Red,background=Color(0xFFF6F7FB))){
        Scaffold(topBar={Row(Modifier.fillMaxWidth().background(Color.White).padding(18.dp),verticalAlignment=Alignment.CenterVertically){
            Text("Life",fontSize=25.sp,fontWeight=FontWeight.Black,color=Ink);Text("Drop",fontSize=25.sp,fontWeight=FontWeight.Black,color=Red)
            Spacer(Modifier.weight(1f));Text(session?.let { "@" + it.username.ifBlank { it.name } } ?: "Guest",fontSize=13.sp,color=Red,fontWeight=FontWeight.SemiBold)
        }},bottomBar={NavigationBar(containerColor=Color.White){
            listOf("Home","Requests","Settings").forEach{p->NavigationBarItem(page==p,{page=p},icon={Icon(when(p){"Home"->Icons.Default.Home;"Requests"->Icons.Default.Notifications;else->Icons.Default.Settings},null)},label={Text(p)})}
        }}){pad->
            Column(Modifier.fillMaxSize().padding(pad)){
                if(notice.isNotBlank()) Text(notice,color=Red,fontSize=12.sp,modifier=Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=5.dp))
                if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                when(page){
                    "Home"->Home(donors,group,{group=it},place,{place=it},busy)
                    "Requests"->RequestList(requests,session,{id->scope.launch{busy=true;try{withContext(Dispatchers.IO){SupabaseClient.fulfill(session!!,id)};refresh++}catch(e:Exception){notice=e.message ?: "Update failed"};busy=false}})
                    "Settings"->Settings(session,usernameSuggestions,{usernameSuggestions=emptyList()},{value->scope.launch{busy=true;try{val found=withContext(Dispatchers.IO){SupabaseClient.usernameSuggestions(value)};usernameSuggestions=found;notice=if(found.isEmpty())"Username @$value is available." else "That username is taken. Try one of these:"}catch(e:Exception){notice=e.message ?: "Could not check username"};busy=false}},{mode,name,username,email,password->
                        scope.launch {
                            busy=true;notice=""
                            try{
                                val result=withContext(Dispatchers.IO){
                                    if(mode=="Sign up") SupabaseClient.signUp(name,username,email,password)
                                    else SupabaseClient.signIn(email,password)
                                }
                                if(result==null){notice="Account created. Check your email to confirm, then log in."; }
                                else {session=result;SupabaseClient.saveSession(context,result);notice="Signed in successfully."}
                            }catch(e:Exception){notice=e.message ?: "Authentication failed"}
                            busy=false
                        }
                    },{SupabaseClient.clearSession(context);session=null;notice="Logged out."},
                    {username->scope.launch{busy=true;try{val updated=session!!.copy(username=username.trim());withContext(Dispatchers.IO){SupabaseClient.setUsername(updated,username)};session=updated;SupabaseClient.saveSession(context,updated);notice="Username saved.";refresh++}catch(e:Exception){notice=e.message ?: "Could not save username"};busy=false}},
                    {n,g,p,c,a->scope.launch{busy=true;try{withContext(Dispatchers.IO){SupabaseClient.addDonor(session!!,n,g,p,c,a)};notice="Donor profile saved to cloud.";refresh++}catch(e:Exception){notice=e.message ?: "Could not save donor"};busy=false}},
                    {n,g,u,h,c,p,urg->scope.launch{busy=true;try{withContext(Dispatchers.IO){SupabaseClient.addRequest(session!!,n,g,u,h,c,p,urg)};notice="Blood request published.";refresh++}catch(e:Exception){notice=e.message ?: "Could not publish request"};busy=false}})
                }
            }
        }
    }
}

@Composable private fun Home(data:List<Donor>,group:String,setGroup:(String)->Unit,place:String,setPlace:(String)->Unit,busy:Boolean){
    val ctx=LocalContext.current
    Column(Modifier.fillMaxSize().padding(16.dp)){
        Text("Welcome to LifeDrop",color=Muted);Text("Find blood. Save lives.",fontSize=27.sp,color=Ink,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(12.dp));Text("Search blood donors",fontSize=19.sp,color=Ink,fontWeight=FontWeight.Bold)
        Text("No account required to search cloud donor records.",fontSize=12.sp,color=Muted)
        GroupPicker(group,setGroup)
        OutlinedTextField(place,setPlace,label={Text("Location: city, area or district")},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp))
        Text(data.size.toString()+" available donor(s)",color=Muted,modifier=Modifier.padding(vertical=8.dp))
        LazyColumn{items(data,key={it.id}){d->Card(Modifier.fillMaxWidth().padding(vertical=4.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){
            Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){
                Text(d.group,color=Red,fontSize=20.sp,fontWeight=FontWeight.Bold)
                Column(Modifier.weight(1f).padding(start=9.dp)){Text(d.name,color=Ink,fontWeight=FontWeight.Bold);Text(d.city,color=Muted,fontSize=12.sp)}
                TextButton(onClick={ctx.startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+d.phone)))}){Icon(Icons.Default.Call,null);Text("Call")}
                TextButton(onClick={ctx.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/"+d.phone.filter{it.isDigit()})))}){Icon(Icons.Default.Chat,null);Text("WhatsApp")}
            }
        }}}
    }
}

@Composable private fun Settings(session:CloudSession?,suggestions:List<String>,clearSuggestions:()->Unit,checkUsername:(String)->Unit,authenticate:(String,String,String,String,String)->Unit,logout:()->Unit,saveUsername:(String)->Unit,
    saveDonor:(String,String,String,String,Boolean)->Unit,saveRequest:(String,String,Int,String,String,String,String)->Unit){

    var mode by remember{mutableStateOf("Login")}
    var section by remember{mutableStateOf("Donor Profile")}
    var name by remember{mutableStateOf("")};var username by remember{mutableStateOf("")};var accountUsername by remember(session?.username){mutableStateOf(session?.username ?: "")};var email by remember{mutableStateOf("")};var password by remember{mutableStateOf("")}
    var donorName by remember{mutableStateOf("")};var donorGroup by remember{mutableStateOf("A+")};var donorPhone by remember{mutableStateOf("")};var donorCity by remember{mutableStateOf("")};var available by remember{mutableStateOf(true)}
    var patient by remember{mutableStateOf("")};var reqGroup by remember{mutableStateOf("A+")};var units by remember{mutableStateOf("1")};var hospital by remember{mutableStateOf("")};var reqCity by remember{mutableStateOf("")};var reqPhone by remember{mutableStateOf("")};var urgency by remember{mutableStateOf("Urgent")}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text("Settings",fontSize=25.sp,color=Ink,fontWeight=FontWeight.Bold)
        if(session==null){
            Text("Your account is used to manage your donor profile and blood requests. Searching remains open to everyone.",color=Muted)
            Row{FilterChip(mode=="Login",{mode="Login"},label={Text("Login")});Spacer(Modifier.width(8.dp));FilterChip(mode=="Sign up",{mode="Sign up"},label={Text("Sign up")})}
            if(mode=="Sign up"){
                Field("Full name",name){name=it}
                Field("Username (unique)",username){username=it.lowercase().filter{ch->ch.isLetterOrDigit()||ch=='_'};clearSuggestions()}
                OutlinedButton(onClick={checkUsername(username)},enabled=username.length>=3&&!usernameSuggestions.contains(username),modifier=Modifier.fillMaxWidth()){Text("Check username availability")}
                if(suggestions.isNotEmpty()){
                    Text("This username is already taken. Available suggestions:",color=Red,fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                        suggestions.forEach{candidate->AssistChip(onClick={username=candidate;clearSuggestions()},label={Text(candidate)})}
                    }
                }
            }
            Field("Email address",email){email=it}
            OutlinedTextField(password,{password=it},label={Text("Password (minimum 6 characters)")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth().padding(vertical=4.dp))
            Button(onClick={authenticate(mode,name.trim(),username.trim(),email.trim(),password)},enabled=email.contains("@")&&password.length>=6&&(mode=="Login"||(name.isNotBlank()&&username.length>=3)),modifier=Modifier.fillMaxWidth()){Text(if(mode=="Login")"Login" else "Create account")}
        }else{
            Card(colors=CardDefaults.cardColors(containerColor=Color.White),modifier=Modifier.fillMaxWidth()){
                Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.AccountCircle,null,tint=Red);Column(Modifier.padding(start=10.dp)){Text("Signed in",fontWeight=FontWeight.Bold,color=Ink);Text(session.name,color=Muted,fontSize=12.sp)}}
            }
            Text("Manage your LifeDrop activity",color=Muted)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                listOf("Donor Profile","Blood Request","Account").forEach{tab->FilterChip(selected=section==tab,onClick={section=tab},label={Text(tab)})}
            }
            when(section){
                "Donor Profile"->{
                    Text("Donor Profile",fontSize=20.sp,fontWeight=FontWeight.Bold,color=Ink)
                    Text("Add or update your donor details.",color=Muted,fontSize=13.sp)
                    Field("Full name",donorName){donorName=it}
                    Text("Blood group",fontWeight=FontWeight.SemiBold,color=Ink);GroupPicker(donorGroup){donorGroup=it}
                    Field("Phone number (include country code for WhatsApp)",donorPhone){donorPhone=it}
                    Field("City / area / district",donorCity){donorCity=it}
                    Row(verticalAlignment=Alignment.CenterVertically){Text("Available to donate",Modifier.weight(1f));Switch(available,{available=it})}
                    Button(onClick={saveDonor(donorName.trim(),donorGroup,donorPhone.trim(),donorCity.trim(),available)},enabled=donorName.isNotBlank()&&donorPhone.filter{it.isDigit()}.length>=10&&donorCity.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("Save donor profile")}
                }
                "Blood Request"->{
                    Text("Post a Blood Request",fontSize=20.sp,fontWeight=FontWeight.Bold,color=Ink)
                    Text("Share the requirement so nearby donors can contact you.",color=Muted,fontSize=13.sp)
                    Field("Patient name",patient){patient=it}
                    Text("Required blood group",fontWeight=FontWeight.SemiBold,color=Ink);GroupPicker(reqGroup){reqGroup=it}
                    Field("Units required",units){units=it.filter{c->c.isDigit()}}
                    Field("Hospital / blood bank",hospital){hospital=it}
                    Field("City / area",reqCity){reqCity=it}
                    Field("Contact phone",reqPhone){reqPhone=it}
                    Text("Urgency",fontWeight=FontWeight.SemiBold,color=Ink)
                    Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("Urgent","Critical","Within 24h").forEach{FilterChip(urgency==it,{urgency=it},label={Text(it)})}}
                    Button(onClick={saveRequest(patient.trim(),reqGroup,units.toIntOrNull()?:1,hospital.trim(),reqCity.trim(),reqPhone.trim(),urgency)},enabled=patient.isNotBlank()&&hospital.isNotBlank()&&reqCity.isNotBlank()&&reqPhone.filter{it.isDigit()}.length>=10&&(units.toIntOrNull()?:0)>0,modifier=Modifier.fillMaxWidth()){Text("Publish blood request")}
                }
                else->{
                    Text("Account",fontSize=20.sp,fontWeight=FontWeight.Bold,color=Ink)
                    Text("You are signed in as " + session.name + ".",color=Muted)
                    Text("Username",fontWeight=FontWeight.SemiBold,color=Ink)
                    Field("Choose a unique username",accountUsername){accountUsername=it.lowercase().filter{ch->ch.isLetterOrDigit()||ch=='_'}}
                    Button(onClick={saveUsername(accountUsername)},enabled=accountUsername.length>=3&&accountUsername!=session.username,modifier=Modifier.fillMaxWidth()){Text("Save username")}
                    OutlinedButton(onClick=logout,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Logout,null);Spacer(Modifier.width(8.dp));Text("Log out")}
                }
            }
        }
    }
}

@Composable private fun RequestList(data:List<BloodRequest>,session:CloudSession?,fulfill:(String)->Unit){
    val ctx=LocalContext.current
    Column(Modifier.fillMaxSize().padding(16.dp)){Text("Blood requests",fontSize=24.sp,color=Ink,fontWeight=FontWeight.Bold)
        LazyColumn{items(data,key={it.id}){r->Card(Modifier.fillMaxWidth().padding(vertical=5.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){
            Column(Modifier.padding(13.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text(r.group,color=Red,fontSize=22.sp,fontWeight=FontWeight.Bold);Column(Modifier.weight(1f).padding(start=10.dp)){Text(r.patient,color=Ink,fontWeight=FontWeight.Bold);Text(r.city+" • "+r.urgency,color=Muted,fontSize=12.sp);Text("Posted by @"+r.username.ifBlank { "unknown" },color=Muted,fontSize=11.sp)};Text(r.status,color=Red)}
                Text(r.units.toString()+" unit(s) • "+r.hospital)
                Row{TextButton(onClick={ctx.startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+r.phone)))}){Icon(Icons.Default.Call,null);Text("Call")};TextButton(onClick={ctx.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/"+r.phone.filter{it.isDigit()})))}){Icon(Icons.Default.Chat,null);Text("WhatsApp")};Spacer(Modifier.weight(1f));if(session!=null&&r.status=="Open")TextButton(onClick={fulfill(r.id)}){Text("Fulfilled")}}
            }
        }}}
    }
}

@Composable private fun GroupPicker(selected:String,choose:(String)->Unit){groups.chunked(4).forEach{row->Row(horizontalArrangement=Arrangement.spacedBy(3.dp)){row.forEach{FilterChip(selected==it,{choose(it)},label={Text(it)})}}}}
@Composable private fun Field(label:String,value:String,change:(String)->Unit){OutlinedTextField(value,change,label={Text(label)},singleLine=true,modifier=Modifier.fillMaxWidth().padding(vertical=4.dp),shape=RoundedCornerShape(12.dp))}
