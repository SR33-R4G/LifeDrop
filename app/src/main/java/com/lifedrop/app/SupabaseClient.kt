package com.lifedrop.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class CloudSession(val accessToken:String,val refreshToken:String,val userId:String,val name:String)

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

    fun signUp(name:String,email:String,password:String):CloudSession? {
        val payload=JSONObject().put("email",email).put("password",password)
            .put("data",JSONObject().put("full_name",name))
        val result=JSONObject(call("POST","/auth/v1/signup",payload))
        return sessionFrom(result,name)
    }

    fun signIn(email:String,password:String):CloudSession {
        val result=JSONObject(call("POST","/auth/v1/token?grant_type=password",JSONObject().put("email",email).put("password",password)))
        return sessionFrom(result,result.optJSONObject("user")?.optJSONObject("user_metadata")?.optString("full_name").orEmpty())
            ?: throw IllegalStateException("Login did not return a session. Check your email confirmation settings.")
    }

    private fun sessionFrom(json:JSONObject,fallbackName:String):CloudSession? {
        val user=json.optJSONObject("user") ?: return null
        val access=json.optString("access_token")
        if(access.isBlank() || access=="null") return null
        val metadata=user.optJSONObject("user_metadata")
        return CloudSession(access,json.optString("refresh_token"),user.optString("id"),
            metadata?.optString("full_name")?.takeIf{!it.isNullOrBlank()} ?: fallbackName)
    }

    fun saveSession(context:Context,session:CloudSession) {
        context.getSharedPreferences("lifedrop_cloud",Context.MODE_PRIVATE).edit()
            .putString("access",session.accessToken).putString("refresh",session.refreshToken)
            .putString("uid",session.userId).putString("name",session.name).apply()
    }
    fun loadSession(context:Context):CloudSession? {
        val p=context.getSharedPreferences("lifedrop_cloud",Context.MODE_PRIVATE)
        val access=p.getString("access",null) ?: return null
        val uid=p.getString("uid",null) ?: return null
        return CloudSession(access,p.getString("refresh","") ?: "",uid,p.getString("name","") ?: "")
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
        val arr=JSONArray(call("GET","/rest/v1/blood_requests?select=*&order=created_at.desc"))
        return (0 until arr.length()).map{val o=arr.getJSONObject(it);BloodRequest(o.getString("id"),o.getString("patient"),o.getString("blood_group"),o.getInt("units"),o.getString("hospital"),o.getString("city"),o.getString("phone"),o.getString("urgency"),o.getString("status"))}
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
data class BloodRequest(val id:String,val patient:String,val group:String,val units:Int,val hospital:String,val city:String,val phone:String,val urgency:String,val status:String)
