import React, { useState, useEffect } from 'react';
import {
  StyleSheet, ScrollView, View, Text, TouchableOpacity, Linking, Platform,
} from 'react-native';

import RNBlockstackSdk from 'react-native-blockstack';

const fname = 'my message/message.txt';

const App = (props) => {

  const [state, setState] = useState({
    loaded: false,
    userData: null,
    fileUrl: null,
    fileContents: null,
  });

  const getDecentralizedID = (result) => {
    if (Platform.OS === 'ios') return result['iss'];
    return result['decentralizedID'];
  };

  const createSession = async () => {

    console.log('blockstack:' + RNBlockstackSdk);

    const hasSession = await RNBlockstackSdk.hasSession();
    if (!hasSession['hasSession']) {
      const config = {
        appDomain: 'https://flamboyant-darwin-d11c17.netlify.app',
        scopes: ['store_write'],
        redirectUrl: '/redirect.html',
        callbackUrlScheme: 'blockstacksample',
      };
      const result = await RNBlockstackSdk.createSession(config);
      console.log('created ' + result['loaded']);
    } else {
      console.log('reusing session');
    }
    setState(prevState => ({ ...prevState, loaded: true }));

    const signedIn = await RNBlockstackSdk.isUserSignedIn();
    if (signedIn['signedIn']) {
      console.log('user is signed in');
      const userData = await RNBlockstackSdk.loadUserData();
      console.log('userData ' + JSON.stringify(userData));
      setState(prevState => ({
        ...prevState,
        userData: { decentralizedID: getDecentralizedID(userData) },
      }));
    }
  };

  const signUp = async () => {
    console.log('signUp');
    try {
      await RNBlockstackSdk.signUp();

      // If Android, app will be called with deep link and need to call handlePendingSignIn in useEffect -> initialUrl or Linking listener to url
      //   The app will continue from there, not below.
      // If iOS, handlePendingSignIn is called by blockstack-ios, no need to call manually. After completion, the app will continue below.
      if (Platform.OS === 'ios') {
        console.log('signUp successfully');
        const userData = await RNBlockstackSdk.loadUserData();
        console.log('userData ' + JSON.stringify(userData));
        setState(prevState => ({
          ...prevState,
          userData: { decentralizedID: getDecentralizedID(userData) },
        }));
      }
    } catch (error) {
      // If user close the window, there will be an error:
      //   The operation couldn’t be completed.
      console.log(error)
    }
  };

  const signIn = async () => {
    console.log('signIn');
    try {
      await RNBlockstackSdk.signIn();
      if (Platform.OS === 'ios') {
        console.log('signIn successfully');
        const userData = await RNBlockstackSdk.loadUserData();
        console.log('userData ' + JSON.stringify(userData));
        setState(prevState => ({
          ...prevState,
          userData: { decentralizedID: getDecentralizedID(userData) },
        }));
      }
    } catch (error) {
      // If user close the window, there will be an error:
      //   The operation couldn’t be completed.
      console.log(error)
    }
  };

  const handlePendingSignIn = async (url) => {
    console.log('handlePendingSignIn');
    const query = url.split(':');
    if (query.length > 1) {
      const parts = query[1].split('=');
      if (parts.length > 1) {
        console.log('deep link ' + parts[1]);
        const result = await RNBlockstackSdk.handlePendingSignIn(parts[1]);
        console.log('handleAuthResponse ' + JSON.stringify(result));
        setState(prevState => ({
          ...prevState,
          userData: { decentralizedID: getDecentralizedID(result) },
        }));
      }
    }
  };

  const signOut = async () => {
    console.log('signOut');
    const result = await RNBlockstackSdk.signUserOut();

    console.log(JSON.stringify(result));
    if (result['signedOut']) {
      setState(prevState => ({ ...prevState, userData: null }));
    }
  };

  const putFile = async () => {
    console.log('putFile');
    setState(prevState => ({ ...prevState, fileUrl: 'uploading...' }));

    //const fname = 'message.txt';
    const content = 'Hello React Native';
    const options = { encrypt: true };
    const result = await RNBlockstackSdk.putFile(fname, content, options);
    console.log(JSON.stringify(result));
    setState(prevState => ({ ...prevState, fileUrl: result['fileUrl'] }));
  };

  const getFile = async () => {
    console.log('getFile');
    setState(prevState => ({ ...prevState, fileContents: 'downloading...' }));

    try {
      //const fname = 'message.txt';
      const options = { decrypt: true };
      const result = await RNBlockstackSdk.getFile(fname, options);
      console.log(JSON.stringify(result));
      setState(prevState => ({ ...prevState, fileContents: result['fileContents'] }));
    } catch (e) {
      console.log(e);
      setState(prevState => ({ ...prevState, fileContents: 'No file or error' }));
    }
  };

  const deleteFile = async () => {
    console.log('deleteFile');
    setState(prevState => ({
      ...prevState,
      fileUrl: 'deleting...',
      fileContents: 'deleting...',
    }));

    //const fname = 'message.txt';
    const options = { wasSigned: false };
    const result = await RNBlockstackSdk.deleteFile(fname, options);
    console.log(JSON.stringify(result));
    setState(prevState => ({ ...prevState, fileUrl: null, fileContents: null }));
  };

  const listFiles = async () => {
    console.log('listFiles');

    const result = await RNBlockstackSdk.listFiles();
    console.log(JSON.stringify(result));
  };

  const updateUserData = async () => {
    /*const userData = {
      "username": "",
      "email": null,
      "profile": {
        "@type": "Person",
        "@context": "http://schema.org",
        "stxAddress": {}
      },
      "decentralizedID": "did:btc-addr:13YyiXjNC1sHTBP6YaTB6ew1FAiHpJE651",
      "identityAddress": "13YyiXjNC1sHTBP6YaTB6ew1FAiHpJE651",
      "appPrivateKey": "<value>",
      "coreSessionToken": null,
      "authResponseToken": null,
      "hubUrl": "https://hub.blockstack.org",
      "coreNode": null,
      "gaiaAssociationToken": "<value>"
    };*/
    const userData = {
      "username": "iiowmang.id.blockstack",
      "email": null,
      "profile": {
        "@type": "Person",
        "@context": "http://schema.org",
        "image": [
          {
            "@type": "ImageObject",
            "name": "avatar",
            "contentUrl": "https://gaia.blockstack.org/hub/1Jkc9emzPhkGR1uG8s3Pe9CCPfWXL2UqCy//avatar-0"
          }
        ],
        "stxAddress": {}
      },
      "decentralizedID": "did:btc-addr:1Jkc9emzPhkGR1uG8s3Pe9CCPfWXL2UqCy",
      "identityAddress": "1Jkc9emzPhkGR1uG8s3Pe9CCPfWXL2UqCy",
      "appPrivateKey": "<value>",
      "coreSessionToken": null,
      "authResponseToken": null,
      "hubUrl": "https://hub.blockstack.org",
      "coreNode": null,
      "gaiaAssociationToken": "<value>"
    };

    const result = await RNBlockstackSdk.updateUserData(userData);
    console.log(JSON.stringify(result));

    const isUserSignedIn = await RNBlockstackSdk.isUserSignedIn();
    if (isUserSignedIn) {
      const userData = await RNBlockstackSdk.loadUserData();
      setState(prevState => ({
        ...prevState,
        userData: { decentralizedID: getDecentralizedID(userData) },
      }));
    }
  };

  useEffect(() => {
    const init = async () => {
      await createSession();

      const initialUrl = await Linking.getInitialURL();
      if (initialUrl) await handlePendingSignIn(initialUrl);

      Linking.addEventListener('url', async (e) => {
        await handlePendingSignIn(e.url);
      });
    };
    init();
  }, []);

  let signInText;
  if (state.userData) signInText = 'Signed in as ' + state.userData.decentralizedID;
  else signInText = 'Not signed in';

  return (
    <View style={styles.container}>
      <Text style={styles.welcome}>Blockstack React Native Example</Text>
      <TouchableOpacity onPress={() => signUp()} disabled={!state.loaded || state.userData != null} style={styles.button}>
        <Text style={styles.buttonText}>Sign up</Text>
      </TouchableOpacity>
      <TouchableOpacity onPress={() => signIn()} disabled={!state.loaded || state.userData != null} style={styles.button}>
        <Text style={styles.buttonText}>Sign In</Text>
      </TouchableOpacity>
      <Text>{signInText}</Text>
      <TouchableOpacity onPress={() => signOut()} disabled={!state.loaded || state.userData == null} style={styles.button}>
        <Text style={styles.buttonText}>Sign out</Text>
      </TouchableOpacity>
      <Text>------------</Text>
      <TouchableOpacity onPress={() => putFile()} disabled={!state.loaded || state.userData == null} style={styles.button}>
        <Text style={styles.buttonText}>Put file</Text>
      </TouchableOpacity>
      <Text>{state.fileUrl}</Text>
      <TouchableOpacity onPress={() => getFile()} disabled={!state.loaded || state.userData == null} style={styles.button}>
        <Text style={styles.buttonText}>Get file</Text>
      </TouchableOpacity>
      <Text>{state.fileContents}</Text>
      <TouchableOpacity onPress={() => deleteFile()} disabled={!state.loaded || state.userData == null} style={styles.button}>
        <Text style={styles.buttonText}>Delete file</Text>
      </TouchableOpacity>
      <TouchableOpacity onPress={() => listFiles()} disabled={!state.loaded || state.userData == null} style={styles.button}>
        <Text style={styles.buttonText}>List files</Text>
      </TouchableOpacity>
      <Text>------------</Text>
      <TouchableOpacity onPress={() => updateUserData()} disabled={!state.loaded} style={styles.button}>
        <Text style={styles.buttonText}>Update user data</Text>
      </TouchableOpacity>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F5FCFF'
  },
  welcome: {
    fontSize: 24,
    textAlign: 'center',
    margin: 10
  },
  button: {
    backgroundColor: '#333333',
    margin: 4,
    paddingTop: 8,
    paddingBottom: 8,
    paddingLeft: 12,
    paddingRight: 12,
  },
  buttonText: {
    fontSize: 16,
    color: 'white',
  },
});

export default App;
