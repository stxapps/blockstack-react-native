import Foundation
import Blockstack

@objc(RNBlockstackSdk)
class RNBlockstackSdk: NSObject {
    
    let defaultErrorCode = "RNBlockstackSdkError"
    var bridge: RCTBridge!
    
    private var config: [String: Any]?
    private var isLoaded = false
  
    @objc static func requiresMainQueueSetup() -> Bool {
        return false
    }

    @objc public func hasSession(_ resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        resolve(["hasSession": self.isLoaded])
    }

    @objc public func createSession(_ config: NSDictionary?, resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        self.config = config as? [String: Any]
        self.isLoaded = true
        resolve(["loaded": self.isLoaded])
    }

    @objc public func isUserSignedIn(_ resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        resolve(["signedIn": Blockstack.shared.isUserSignedIn()])
    }

    @objc public func signIn(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {

        guard let config = self.config,
              let appDomainString = config["appDomain"] as? String,
              let appDomain = URL(string: appDomainString),
              let redirectUrlString = config["redirectUrl"] as? String,
              let redirectURL = URL(string: redirectUrlString, relativeTo: appDomain) else {
            reject(self.defaultErrorCode, "Invalid Blockstack session config data", nil)
            return
        }

        var manifestURI: URL?
        if let manifestPath = config["manifestUrl"] as? String {
            manifestURI = URL(string: manifestPath)
        }

        let scopes = (config["scopes"] as? [String] ?? ["store_write"]).compactMap { AuthScope.fromString($0) }

        Blockstack.shared.signIn(redirectURI: redirectURL, appDomain: appDomain, manifestURI: manifestURI, scopes: scopes) { authResult in
            switch authResult {
            case let .success(userData: userData):
                let data = [
                    "result": "success",
                    "user_data": userData.dictionary ?? [],
                    "decentralizedID": userData.username ?? "",
                    "loaded": true,
                ] as [String : Any]
                resolve(data)
                return
            case let .failed(err):
                let errMsg = err?.localizedDescription ?? "Error"
                reject(self.defaultErrorCode, errMsg, err)
                return
            case .cancelled:
                reject(self.defaultErrorCode, "signIn cancelled", nil)
                return
            }
        }
    }

    @objc public func signUserOut(_ resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        Blockstack.shared.signUserOut()
        resolve(["signedOut": true])
    }
    
    @objc public func loadUserData(_ resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {

        let userData = Blockstack.shared.loadUserData()
        guard userData != nil else {
            reject(self.defaultErrorCode, "loadUserData returns nil", nil)
            return
        }

        resolve(userData.dictionary ?? [])
    }
    
    @objc public func putFile(_ fileName: String!, content: String!, options: NSDictionary?, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        let encrypt = options?["encrypt"] as? Bool ?? true
        let escapedFileName = fileName.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed)!
        Blockstack.shared.putFile(to: escapedFileName, text: content, encrypt: encrypt) { result, error in
            guard let fileUrl = result, error == nil else {
                reject(self.defaultErrorCode, "putFile Error", error)
                return
            }
            resolve(["fileUrl": fileUrl])
        }
    }
    
    @objc public func getFile(_ path: String!, options: NSDictionary?, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        // TODO: Support multiplayer
        // let username = options?["username"] as? String
        let decrypt = options?["decrypt"] as? Bool ?? true
        let escapedPath = path.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed)!
        Blockstack.shared.getFile(at: escapedPath, decrypt: decrypt) {
            value, error in

            guard error == nil else {
                reject(self.defaultErrorCode, "getFile Error", error)
                return
            }
            
            if decrypt {
                guard let decryptedValue = value as? DecryptedValue else {
                    reject(self.defaultErrorCode, "In getFile, options decrypt is true but value is not DecryptedValue.", error)
                    return
                }

                if decryptedValue.isString {
                    resolve(["fileContents": decryptedValue.plainText])
                    return
                }
                
                resolve(["fileContentsEncoded": decryptedValue.bytes?.toBase64()])
                return
            }

            resolve(["fileContents": value])
        }
    }

    @objc public func deleteFile(_ path: String!, options: NSDictionary?, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {

        let wasSigned = options?["wasSigned"] as? Bool ?? false
        let escapedPath = path.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed)!
        Blockstack.shared.deleteFile(at: escapedPath, wasSigned: wasSigned) {
            error in

            guard error == nil else {
                reject(self.defaultErrorCode, "deleteFile Error", error)
                return
            }

            resolve(["deleted": true])
        }
    }

    @objc public func listFiles(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {

        // list all files and return to JS just once
        var files = [String]()
        Blockstack.shared.listFiles(callback: {
            files.append($0)
            return true
        }, completion: { fileCount, error in

            guard error == nil else {
                reject(self.defaultErrorCode, "listFiles Error", error)
                return
            }

            resolve(["files": files, "fileCount": fileCount])
        })
    }
}

extension Encodable {
    var dictionary: [String: Any]? {
        guard let data = try? JSONEncoder().encode(self) else { return nil }
        return (try? JSONSerialization.jsonObject(with: data, options: .allowFragments)).flatMap { $0 as? [String: Any] }
    }
}
