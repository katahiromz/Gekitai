// 痴漢撃退アプリ「警告君」
// 暗号名はGekitai。

const geki_VERSION = '1.0.1'; // Keikokuバージョン番号。
const geki_APP_NAME = "撃退君"; // アプリの名前
let geki_alert_audio = new Audio('audio/alert.mp3'); // 警告音。
let geki_alerting = false; // 警報中か？
let geki_request_anime = null; // アニメーション要求。

// 【Gekitai JavaScript 命名規則】
// - 関数名の頭に GEKI_ を付ける。
// - 変数名・定数名の頭に geki_ を付ける。
// - 要素IDの頭に geki_id_ を付ける。
// - CSSクラスの頭に geki_class_ を付ける。

// 【Gekitai コーディング ルール】
// - インデントはTabを使うこと。
// - 言語特有の記述が必要な個所は「{{LANGUAGE_SPECIFIC}}」というコメントを付けること。
// - 関数はすべてconst変数にすること。functionキーワードは省略しないこと。

// このアプリはAndroidアプリか？
const GEKI_is_android_app = function(){
	return navigator.userAgent.indexOf('/Keikoku-android-app/') != -1;
};

// Androidアプリならバージョン番号を取得する。
const GEKI_get_android_app_version = function(){
	let results = navigator.userAgent.match(/\/Keikoku-android-app\/([\d\.]+)\//);
	if(results)
		return results[1];
	return false;
};

// HTMLの特殊文字を置換。
const GEKI_htmlspecialchars = function(str){
	return (str + '').replace(/&/g,'&amp;').replace(/"/g,'&quot;').replace(/'/g,'&#039;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); 
};

// ローカルストレージから設定を読み込む。設定がなければ初期化する。
const GEKI_load_local_storage = function(){
	;
};

// 警報中かどうかをセットする。
const GEKI_setAlerming = function(value = 'yes'){
	console.log('GEKI_setAlerming');
	try{
		android.setAlerming(value);
	}catch(error){ // Androidではない。
		;
	}
};

// 音声を最大化する。
const GEKI_volume_maximize = function(){
	console.log('GEKI_volume_maximize');
	try{
		android.volumeMaximize();
	}catch(error){ // Androidではない。
		;
	}
};

// 画面の明るさを最大化する。
const GEKI_brightness_set = function(maximize){
	console.log('GEKI_brightness_set');
	try{
		android.setBrightness(maximize ? 'brighter' : 'normal');
	}catch(error){ // Androidではない。
		;
	}
};

// 音声合成を中断する。
const GEKI_speak_cancel = function(){
	console.log('GEKI_speak_cancel');
	try{
		android.cancelSpeech(); // スピーチのキャンセル。Androidでなければ失敗。
	}catch(error){ // Androidではない。
		// Web側でスピーチをキャンセル。
		if(window.speechSynthesis){
			window.speechSynthesis.cancel();
		}
	}
};

// 音声合成を開始する。
const GEKI_speak_start = function(text, once_only = false){
	console.log('GEKI_speak_start');

	// 開始する前にいったんキャンセルする。
	GEKI_speak_cancel();

	try{
		// Android側のスピーチを開始する。Androidでなければ失敗。
		android.speechLoop(text, "1.0");
	}catch(error){ // Androidではない。Web側のスピーチを開始する。
		if(window.speechSynthesis){
			if(!once_only)
				text = text.repeat(32); // 32回繰り返す。
			const utter = new SpeechSynthesisUtterance(text);
			utter.pitch = 1.0; // 音声の高さ。
			utter.rate = 1.8; // 音声の速さ。
			utter.volume = 2.0; // 音量。
			// {{LANGUAGE_SPECIFIC}}: スピーチの言語をセットする。
			utter.lang = 'ja-JP';
			// 実際にスピーチを開始する。
			window.speechSynthesis.speak(utter);
		}
	}
};

// 振動を開始する。
const GEKI_vibrator_start = function(strength){
	console.log('GEKI_vibrator_start');
	try{
		android.startVibrator(strength.toString());
	}catch(error){ // Androidではない。
		;
	}
};

// 振動を停止する。
const GEKI_vibrator_stop = function(strength){
	console.log('GEKI_vibrator_stop');
	try{
		android.stopVibrator();
	}catch(error){ // Androidではない。
		;
	}
};

// メッセージテキストの位置をゆらゆら動かす。
const GEKI_message_set_position = function(id, counter){
	// 上下に揺らす。
	let top = 50 + Math.sin(counter * 0.01) * 8;
	id.style.transform = `translate(-50%, -${top}%)`;
	// ついでに色も変える。
	let color = ((counter % 1000) < 500) ? 'yellow' : 'white';
	let bgColor = ((counter % 1000) < 500) ? '#f00' : '#900';
	id.style.color = color;
	id.style.backgroundColor = bgColor;
};

// 画面遷移。
const GEKI_choose_page = function(page_id){
	// まず、すべてのページを隠す。
	let pages = document.getElementsByClassName('geki_class_page');
	for(let page of pages){
		page.classList.add('geki_class_invisible');
	}
	// 一つのページを表示する。
	if(typeof(page_id) == 'string')
		page_id = document.getElementById(page_id);
	page_id.classList.remove('geki_class_invisible');

	if(page_id == geki_id_page_alert){ // 撃退ページであれば
		geki_alerting = true;
		GEKI_alert(true);
	}else{ // さもなくば
		geki_alerting = false;
		GEKI_alert(false);
	}
};

// イベントリスナー群を登録する。
const GEKI_register_event_listeners = function(){
	// はてなボタン。
	geki_id_button_about.addEventListener('click', function(e){
		// 画面遷移。
		GEKI_choose_page(geki_id_page_agreement);
	});
	// 痴漢撃退ボタン。
	geki_id_button_gekitai.addEventListener('click', function(e){
		// 画面遷移。
		GEKI_choose_page(geki_id_page_alert);
	});
	// 同意ページの戻るボタン。
	geki_id_button_agreement_back.addEventListener('click', function(e){
		// 画面遷移。
		GEKI_choose_page(geki_id_page_main);
	});
	// 同意ページのOKボタン。
	geki_id_button_agree_ok.addEventListener('click', function(e){
		// 画面遷移。
		GEKI_choose_page(geki_id_page_main);
	});
	// 撃退ページの戻るボタン。
	geki_id_button_gekitai_back.addEventListener('click', function(e){
		// 画面遷移。
		GEKI_choose_page(geki_id_page_main);
	});
	// 警報ページの解除ボタン。
	geki_id_button_alert_cancel.addEventListener('click', function(e){
		// 画面遷移。
		GEKI_choose_page(geki_id_page_main);
	});
	// ページを移動しようとしたら、音声を止める。
	window.addEventListener('unload', function(e){
		// 音声を停止する。
		geki_alert_audio.pause();
		GEKI_speak_cancel();
	});
};

// バージョン情報の読み取り。
const GEKI_load_version_info = function(){
	const pre_span = '<span class="geki_class_nobr">';
	const post_span = '</span>';
	geki_id_text_version.innerHTML =
		pre_span + GEKI_htmlspecialchars(geki_APP_NAME) + post_span + " " +
		pre_span + "Version" + post_span + " " +
		pre_span + GEKI_htmlspecialchars(geki_VERSION) + post_span;
};

// アニメーションを実行するコールバック関数。
const GEKI_anime_callback = function(timestamp){
	GEKI_message_set_position(geki_id_text_floating_1, timestamp);
	// 必要ならアニメーションを要求する。
	if(geki_request_anime)
		geki_request_anime = window.requestAnimationFrame(GEKI_anime_callback);
};

// 警報を鳴らす。
const GEKI_alert = function(do_start = true){
	if(do_start){
		// 音声を最大化する。
		GEKI_volume_maximize();
		// 音声を開始する。
		geki_alert_audio.volume = 1.0;
		geki_alert_audio.currentTime = 0;
		geki_alert_audio.loop = true;
		geki_alert_audio.play();
		GEKI_speak_start("痴漢です。痴漢です。助けてください。HELP！ ");
		// 浮遊するテキストを表示。
		geki_id_text_floating_1.classList.remove('geki_class_invisible');
		// 画面の明るさを最大にする。
		GEKI_brightness_set(true);
		// 振動を開始する。
		GEKI_vibrator_start(5);
		// 警報中かどうかをセットする。
		GEKI_setAlerming('yes');
		// 必要ならアニメーションを要求する。
		if(!geki_request_anime){
			geki_request_anime = window.requestAnimationFrame(GEKI_anime_callback);
		}
	}else{
		// アニメーションをキャンセルする。
		if(geki_request_anime){
			window.cancelAnimationFrame(geki_request_anime);
			geki_request_anime = null;
		}
		// 警報中かどうかをセットする。
		GEKI_setAlerming('no');
		// 音声を停止する。
		geki_alert_audio.pause();
		GEKI_speak_cancel();
		// 浮遊するテキストを非表示。
		geki_id_text_floating_1.classList.add('geki_class_invisible');
		// 画面の明るさを元に戻す。
		GEKI_brightness_set(false);
		// 振動を停止する。
		GEKI_vibrator_stop();
	}
};

// 一時停止。
const GEKI_pause = function() {
	if(geki_alerting){
		GEKI_alert(false);
	}
};

// 再開。
const GEKI_resume = function() {
	if(geki_alerting){
		GEKI_alert(true);
	}
};

// メイン関数。
const GEKI_main = function(){
	const argc = arguments.length, argv = arguments;
	// バージョン情報の読み取り。
	GEKI_load_version_info();
	// 設定をローカルストレージから読み込む。
	GEKI_load_local_storage();
	// イベントリスナー群を登録する。
	GEKI_register_event_listeners();
	// AndroidアプリでなければAndroidオンリーの要素を隠す。
	if(!GEKI_is_android_app()){
		let items = document.getElementsByClassName('geki_class_android_app_only');
		for(let item of items){
			item.classList.add('geki_class_invisible');
		}
	}
	// 念のため、音声を停止する。
	GEKI_speak_cancel();
	// スマホ・タブレットでのズームを禁止する。
	document.addEventListener('touchmove', function(e){
		if(e.scale !== 1){
			e.preventDefault();
		}
	},{ passive: false });
	// Ctrl+ホイールでのズームを禁止する。
	document.addEventListener('wheel', function(e){
		if(e.ctrlKey){
			e.preventDefault();
		}
	},{ passive: false });
};

// ドキュメントの読み込みが完了（DOMContentLoaded）されたら無名関数が呼び出される。
document.addEventListener('DOMContentLoaded', function(){
	// メイン関数を呼び出す。
	GEKI_main();
});
