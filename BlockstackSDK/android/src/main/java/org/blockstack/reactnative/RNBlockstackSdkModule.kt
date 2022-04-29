package org.blockstack.reactnative

import android.util.Base64
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableType
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.blockstack.android.sdk.BlockstackSession
import org.blockstack.android.sdk.BlockstackSignIn
import org.blockstack.android.sdk.Result
import org.blockstack.android.sdk.Scope
import org.blockstack.android.sdk.SessionStore
import org.blockstack.android.sdk.ecies.signContent
import org.blockstack.android.sdk.getBlockstackSharedPreferences
import org.blockstack.android.sdk.model.BlockstackConfig
import org.blockstack.android.sdk.model.GetFileOptions
import org.blockstack.android.sdk.model.PutFileOptions
import org.blockstack.android.sdk.model.DeleteFileOptions
import org.blockstack.android.sdk.model.UserData
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URI

class RNBlockstackSdkModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private var session: BlockstackSession? = null
    private var blockstackSignIn: BlockstackSignIn? = null

    override fun getName(): String {
        return "RNBlockstackSdk"
    }

    @ReactMethod
    fun hasSession(promise: Promise) {
        try {
            val map = Arguments.createMap()
            map.putBoolean("hasSession", session != null)
            promise.resolve(map)
        } catch (e: Exception) {
            Log.d(name, "Error in hasSession: $e")
            promise.reject(e)
        }
    }

    @ReactMethod
    fun createSession(configArg: ReadableMap, promise: Promise) {

        if (currentActivity == null) {
            Log.d(name, "reject create session as the activity is null")
            promise.reject(IllegalStateException("must be called from an Activity"))
            return
        }

        try {
            val sessionStore = SessionStore(currentActivity!!.getBlockstackSharedPreferences())

            val scopes = configArg.getArray("scopes")?.toArrayList()?.map {
                Scope.fromJSName(it as String).scope
            }?.toTypedArray()

            if (!configArg.hasKey("appDomain")) {
                promise.reject(IllegalArgumentException("'appDomain' needed in config object"))
                return
            }

            val appDomain = configArg.getString("appDomain")
            val manifestPath = if (configArg.hasKey("manifestUrl")) {
                configArg.getString("manifestUrl")
            } else {
                "/manifest.json"
            }

            val redirectPath = if (configArg.hasKey("redirectUrl")) {
                configArg.getString("redirectUrl")
            } else {
                "/redirect"
            }
            val config = BlockstackConfig(URI(appDomain), redirectPath!!, manifestPath!!, scopes!!)

            session = BlockstackSession(sessionStore, config)
            blockstackSignIn = BlockstackSignIn(sessionStore, config)

            val map = Arguments.createMap()
            map.putBoolean("loaded", true)
            promise.resolve(map)
        } catch (e: Exception) {
            Log.d(name, "Error in createSession: $e")
            promise.reject(e)
        }
    }

    @ReactMethod
    fun isUserSignedIn(promise: Promise) {
        if (session == null) {
            promise.reject("NOT_LOADED", "Session not loaded")
            return
        }

        try {
            val map = Arguments.createMap()
            map.putBoolean("signedIn", session!!.isUserSignedIn())
            promise.resolve(map)
        } catch (e: Exception) {
            Log.d(name, "Error in isUserSignedIn: $e")
            promise.reject(e)
        }
    }

    @ReactMethod
    fun signUp(promise: Promise) {
        if (session == null || currentActivity == null) {
            promise.reject("NOT_LOADED", "Session not loaded or current activity is null")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                blockstackSignIn!!.redirectUserToSignIn(currentActivity!!, false)
                promise.resolve(null)
            } catch (e: Exception) {
                Log.d(name, "Error in signIn: $e")
                promise.reject(e)
            }
        }
    }

    @ReactMethod
    fun signIn(promise: Promise) {
        if (session == null || currentActivity == null) {
            promise.reject("NOT_LOADED", "Session not loaded or current activity is null")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                blockstackSignIn!!.redirectUserToSignIn(currentActivity!!, true)
                promise.resolve(null)
            } catch (e: Exception) {
                Log.d(name, "Error in signIn: $e")
                promise.reject(e)
            }
        }
    }

    @ReactMethod
    fun handlePendingSignIn(authResponse: String, promise: Promise) {
        if (session == null) {
            promise.reject("NOT_LOADED", "Session not loaded")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val res = session!!.handlePendingSignIn(authResponse)
                if (res.hasValue) {
                    val map = convertJsonToMap(res.value!!.json)
                    map.putBoolean("loaded", true)
                    promise.resolve(map)
                } else {
                    promise.reject(Error(res.error?.toString()))
                }
            } catch (e: Exception) {
                Log.d(name, "Error in handlePendingSignIn: $e")
                promise.reject(e)
            }
        }
    }

    @ReactMethod
    fun signUserOut(promise: Promise) {
        if (session == null) {
            promise.reject("NOT_LOADED", "Session not loaded")
            return
        }

        try {
            session!!.signUserOut()
            val map = Arguments.createMap()
            map.putBoolean("signedOut", true)
            promise.resolve(map)
        } catch (e: Exception) {
            Log.d(name, "Error in signUserOut: $e")
            promise.reject(e)
        }
    }

    @ReactMethod
    fun updateUserData(userData: ReadableMap, promise: Promise) {
        if (session == null) {
            promise.reject("NOT_LOADED", "Session not loaded")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                session!!.updateUserData(UserData(convertMapToJson(userData)))

                val map = Arguments.createMap()
                map.putBoolean("updated", true)
                promise.resolve(map)
            } catch (e: Exception) {
                Log.d(name, "Error in updateUserData: $e")
                promise.reject(e)
            }
        }
    }

    @ReactMethod
    fun loadUserData(promise: Promise) {
        if (session == null) {
            promise.reject("NOT_LOADED", "Session not loaded")
            return
        }

        try {
            val userData = session!!.loadUserData()
            promise.resolve(convertJsonToMap(userData.json))
        } catch (e: Exception) {
            Log.d(name, "Error in loadUserData: $e")
            promise.reject(e)
        }
    }

    @ReactMethod
    fun putFile(path: String, content: String, optionsArg: ReadableMap, promise: Promise) {
        if (session == null) {
            Log.d(name, "reject put file")
            promise.reject(IllegalStateException("In putFile, session is null"))
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val encrypt = optionsArg.getBoolean("encrypt")
            val options = if (optionsArg.hasKey("dir")) {
                PutFileOptions(encrypt, dir = optionsArg.getString("dir")!!)
            } else PutFileOptions(encrypt)

            val res = session!!.putFile(path, content, options)
            if (res.hasValue) {
                val map = Arguments.createMap()
                map.putString("fileUrl", res.value)
                promise.resolve(map)
            } else {
                promise.reject(Error(res.error?.toString()))
            }
        }
    }

    @ReactMethod
    fun getFile(path: String, optionsArg: ReadableMap, promise: Promise) {
        if (session == null) {
            Log.d(name, "reject get file")
            promise.reject(IllegalStateException("In getFile, session is null"))
        }

        CoroutineScope(Dispatchers.IO).launch {
            val decrypt = optionsArg.getBoolean("decrypt")
            val options = if (optionsArg.hasKey("dir")) {
                GetFileOptions(decrypt, dir = optionsArg.getString("dir")!!)
            } else GetFileOptions(decrypt)

            val res = session!!.getFile(path, options)
            if (res.hasValue) {
                val map = Arguments.createMap()
                if (res.value is String) {
                    map.putString("fileContents", res.value as String)
                } else {
                    map.putString("fileContentsEncoded", Base64.encodeToString(res.value as ByteArray, Base64.NO_WRAP))
                }
                promise.resolve(map)
            } else {
                promise.reject(Error(res.error?.toString()))
            }
        }
    }

    @ReactMethod
    fun deleteFile(path: String, optionsArg: ReadableMap, promise: Promise) {
        if (session == null) {
            Log.d(name, "reject delete file")
            promise.reject(IllegalStateException("In deleteFile, session is null"))
        }

        CoroutineScope(Dispatchers.IO).launch {
            val options = DeleteFileOptions(optionsArg.getBoolean("wasSigned"))
            val res = session!!.deleteFile(path, options)
            if (res.hasErrors) {
                promise.reject(Error(res.error?.toString()))
            } else {
                val map = Arguments.createMap()
                map.putBoolean("deleted", true)
                promise.resolve(map)
            }
        }
    }

    @ReactMethod
    fun listFiles(promise: Promise) {
        // React native only supports one promise or two callbacks
        //   and cannot mix them together.
        // https://github.com/facebook/react-native/issues/14702
        if (session == null) {
            Log.d(name, "reject list files")
            promise.reject(IllegalStateException("in listFiles, session is null"))
        }

        CoroutineScope(Dispatchers.IO).launch {
            // list all files and return to JS just once
            val files = ArrayList<String>()
            val res = session!!.listFiles { result: Result<String> ->
                result.value?.let { files.add(it) }
                true
            }
            if (res.hasValue) {
                val map = Arguments.createMap()
                map.putArray("files", Arguments.fromList(files))
                map.putInt("fileCount", res.value!!)
                promise.resolve(map)
            } else {
                promise.reject(Error(res.error?.toString()))
            }
        }
    }

    @ReactMethod
    fun signECDSA(privateKey: String, content: String, promise: Promise) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // @stacks/encryption uses noble-secp256k1
                //   and in noble-secp256k1/index.ts#L1148, default canonical is true.
                val res = signContent(content, privateKey, true)

                val map = Arguments.createMap()
                map.putString("publicKey", res.publicKey)
                map.putString("signature", res.signature)
                promise.resolve(map)
            } catch (e: Exception) {
                Log.d(name, "Error in signECDSA: $e")
                promise.reject(e)
            }
        }
    }

    @Throws(JSONException::class)
    private fun convertJsonToMap(jsonObject: JSONObject): WritableMap {
        val map = Arguments.createMap()

        val iterator = jsonObject.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            val value = jsonObject.get(key)
            if (value is JSONObject) {
                map.putMap(key, convertJsonToMap(value))
            } else if (value is JSONArray) {
                map.putArray(key, convertJsonToArray(value))
            } else if (value is Boolean) {
                map.putBoolean(key, value)
            } else if (value is Int) {
                map.putInt(key, value)
            } else if (value is Double) {
                map.putDouble(key, value)
            } else if (value is String) {
                map.putString(key, value)
            } else {
                map.putString(key, value.toString())
            }
        }
        return map
    }

    @Throws(JSONException::class)
    private fun convertJsonToArray(jsonArray: JSONArray): WritableArray {
        val array = Arguments.createArray()

        for (i in 0 until jsonArray.length()) {
            val value = jsonArray.get(i)
            if (value is JSONObject) {
                array.pushMap(convertJsonToMap(value))
            } else if (value is JSONArray) {
                array.pushArray(convertJsonToArray(value))
            } else if (value is Boolean) {
                array.pushBoolean(value)
            } else if (value is Int) {
                array.pushInt(value)
            } else if (value is Double) {
                array.pushDouble(value)
            } else if (value is String) {
                array.pushString(value)
            } else {
                array.pushString(value.toString())
            }
        }
        return array
    }

    private fun convertMapToJson(readableMap: ReadableMap): JSONObject {
        val jsonObject = JSONObject()

        val iterator = readableMap.keySetIterator();
        while (iterator.hasNextKey()) {
            val key = iterator.nextKey()
            when (readableMap.getType(key)) {
                ReadableType.Null -> {
                    jsonObject.put(key, JSONObject.NULL);
                }
                ReadableType.Boolean -> {
                    jsonObject.put(key, readableMap.getBoolean(key));
                }
                ReadableType.Number -> {
                    jsonObject.put(key, readableMap.getDouble(key));
                }
                ReadableType.String -> {
                    jsonObject.put(key, readableMap.getString(key));
                }
                ReadableType.Map -> {
                    jsonObject.put(key, convertMapToJson(readableMap.getMap(key)!!));
                }
                ReadableType.Array -> {
                    jsonObject.put(key, convertArrayToJson(readableMap.getArray(key)!!));
                }
            }
        }
        return jsonObject
    }

    private fun convertArrayToJson(readableArray: ReadableArray): JSONArray {
        val jsonArray = JSONArray();

        for (i in 0 until readableArray.size()) {
            when (readableArray.getType(i)) {
                ReadableType.Null -> {
                    break
                }
                ReadableType.Boolean -> {
                    jsonArray.put(readableArray.getBoolean(i));
                }
                ReadableType.Number -> {
                    jsonArray.put(readableArray.getDouble(i));
                }
                ReadableType.String -> {
                    jsonArray.put(readableArray.getString(i));
                }
                ReadableType.Map -> {
                    jsonArray.put(convertMapToJson(readableArray.getMap(i)!!));
                }
                ReadableType.Array -> {
                    jsonArray.put(convertArrayToJson(readableArray.getArray(i)!!));
                }
            }
        }
        return jsonArray
    }
}
