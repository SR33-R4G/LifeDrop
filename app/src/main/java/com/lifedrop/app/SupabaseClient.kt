package com.lifedrop.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class CloudSession(val accessToken:String,val refreshToken:String,val userId:String,val name:String,val username:String="")

object SupabaseClient {
    private const val BASE_URL = "https://mxpswriqgeubscaeuxyx.supabase.co"
    private const val PUBLISHABLE_KEY = "sb_publishable_sG7cyvm1WIMZnzMrjppdDA_X1OxMqLO"

    private fun call(method:String,path:String,body:JSONObject?=null,token:String?=null,prefer:String?=null):String {
        val connection=(URL(BASE_URL+path).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod=method
            connection.connectTimeout=15000
            connection.readTimeout=20000
            connection.setRequestProperty("apikey",PUBLISHABLE_KEY)
            if(token!=null) connection.setRequestProperty("Authorization","Bearer $token")
            connection.setRequestProperty("Content-Type","application/json")
            connection.setRequestProperty("Accept","application/json")
            if(prefer!=null) connection.setRequestProperty("Prefer",prefer)
            if(body!=null){connection.doOutput=true;connection.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}}
            val code=connection.responseCode
            val stream=if(code in 200..299) connection.inputStream else connection.errorStream
            val response=stream?.bufferedReader()?.use{it.readText()} ?: ""
            if(code !in 200..299) {
                val message=try{JSONObject(response).optString("message",JSONObject(response).optString("msg",response))}catch(_:Exception){response}
                throw IllegalStateException("Supabase error ($code): $message")
            }
            return response
        } finally { connection.disconnect() }
    }

    fun signUp(name:String,username:String,email:String,password:String):CloudSession? {
        val payload=JSONObject().put("email",email).put("password",password)
            .put("data",JSONObject().put("full_name",name).put("username",username))
        val result=JSONObject(call("POST","/auth/v1/signup",payload))
        val session=sessionFrom(result,name)
        if(session!=null) setUsername(session,username)
        return session
    }

    fun signIn(email:String,password:String):CloudSession {
        val result=JSONObject(call("POST","/auth/v1/token?grant_type=password",JSONObject().put("email",email).put("password",password)))
        val session=sessionFrom(result,result.optJSONObject("user")?.optJSONObject("user_metadata")?.optString("full_name").orEmpty())
            ?: throw IllegalStateException("Login did not return a session. Check your email confirmation settings.")
        val username=profileUsername(session)
        return session.copy(username=username.ifBlank { session.username })
    }

    private fun sessionFrom(json:JSONObject,fallbackName:String):CloudSession? {
        val user=json.optJSONObject("user") ?: return null
        val access=json.optString("access_token")
        if(access.isBlank() || access=="null") return null
        val metadata=user.optJSONObject("user_metadata")
        return CloudSession(access,json.optString("refresh_token"),user.optString("id"),
            metadata?.optString("full_name")?.takeIf{!it.isNullOrBlank()} ?: fallbackName,
            metadata?.optString("username")?.takeIf{!it.isNullOrBlank()} ?: "")
    }

    fun saveSession(context:Context,session:CloudSession) {
        context.getSharedPreferences("lifedrop_cloud",Context.MODE_PRIVATE).edit()
            .putString("access",session.accessToken).putString("refresh",session.refreshToken)
            .putString("uid",session.userId).putString("name",session.name).putString("username",session.username).apply()
    }
    fun loadSession(context:Context):CloudSession? {
        val p=context.getSharedPreferences("lifedrop_cloud",Context.MODE_PRIVATE)
        val access=p.getString("access",null) ?: return null
        val uid=p.getString("uid",null) ?: return null
        return CloudSession(access,p.getString("refresh","") ?: "",uid,p.getString("name","") ?: "",p.getString("username","") ?: "")
    }
    fun clearSession(context:Context){context.getSharedPreferences("lifedrop_cloud",Context.MODE_PRIVATE).edit().clear().apply()}

    fun donors(group:String,city:String):List<Donor> {
        val filters=mutableListOf("select=id,name,blood_group,phone,city,available","available=eq.true")
        if(group!="All") filters.add("blood_group=eq."+enc(group))
        if(city.isNotBlank()) filters.add("city=ilike.*"+enc(city)+"*")
        filters.add("order=name.asc")
        val arr=JSONArray(call("GET","/rest/v1/donors?"+filters.joinToString("&")))
        return (0 until arr.length()).map{val o=arr.getJSONObject(it);Donor(o.getString("id"),o.getString("name"),o.getString("blood_group"),o.getString("phone"),o.getString("city"))}
    }
    fun requests():List<BloodRequest> {
        val arr=JSONArray(call("GET","/rest/v1/blood_requests?select=id,owner_id,patient,blood_group,units,hospital,city,phone,urgency,status,profiles(username)&order=created_at.desc"))
        return (0 until arr.length()).map{val o=arr.getJSONObject(it);BloodRequest(o.getString("id"),o.getString("owner_id"),o.getString("patient"),o.getString("blood_group"),o.getInt("units"),o.getString("hospital"),o.getString("city"),o.getString("phone"),o.getString("urgency"),o.getString("status"),o.optJSONObject("profiles")?.optString("username").orEmpty())}
    }
    fun profileUsername(s:CloudSession):String {
        val arr=JSONArray(call("GET","/rest/v1/profiles?select=username&id=eq."+enc(s.userId),token=s.accessToken))
        return if(arr.length()>0) arr.getJSONObject(0).optString("username") else ""
    }
    fun setUsername(s:CloudSession,username:String) {
        val body=JSONObject().put("id",s.userId).put("username",username.trim())
        call("POST","/rest/v1/profiles?on_conflict=id",body,s.accessToken,"resolution=merge-duplicates,return=minimal")
    }

    fun usernameSuggestions(username:String):List<String> {
        val base=username.trim().lowercase()
        require(base.length>=3) { "Username must contain at least 3 characters." }
        val candidates=(1..40).map { base + (100..999).random() }.distinct()
        val requested=(listOf(base)+candidates).distinct()
        val filter=requested.joinToString(",") { "\"$it\"" }
        val arr=JSONArray(call("GET","/rest/v1/profiles?select=username&username=in.("+filter+")"))
        val existing=(0 until arr.length()).map { arr.getJSONObject(it).optString("username").lowercase() }.toSet()
        if(base !in existing) return emptyList()
        val available=candidates.filter { it !in existing }.distinct().take(3)
        if(available.size<3) throw IllegalStateException("Could not generate suggestions. Please try again.")
        return available
    }
    fun addDonor(s:CloudSession,n:String,g:String,p:String,c:String,a:Boolean) {
        call("POST","/rest/v1/donors",JSONObject().put("owner_id",s.userId).put("name",n).put("blood_group",g).put("phone",p).put("city",c).put("available",a),s.accessToken,"return=minimal")
    }
    fun addRequest(s:CloudSession,n:String,g:String,u:Int,h:String,c:String,p:String,urg:String) {
        call("POST","/rest/v1/blood_requests",JSONObject().put("owner_id",s.userId).put("patient",n).put("blood_group",g).put("units",u).put("hospital",h).put("city",c).put("phone",p).put("urgency",urg),s.accessToken,"return=minimal")
    }
    fun fulfill(s:CloudSession,id:String) {
        call("PATCH","/rest/v1/blood_requests?id=eq."+enc(id),JSONObject().put("status","Fulfilled"),s.accessToken,"return=minimal")
    }
    private fun enc(value:String)=URLEncoder.encode(value,"UTF-8")
}

data class Donor(val id:String,val name:String,val group:String,val phone:String,val city:String)
data class BloodRequest(val id:String,val ownerId:String,val patient:String,val group:String,val units:Int,val hospital:String,val city:String,val phone:String,val urgency:String,val status:String,val username:String)
