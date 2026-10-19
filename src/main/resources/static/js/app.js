var db={patients:[],doctors:[],appointments:[]};
async function api(path,method,body){
 var r=await fetch("/api/"+path,{method:method||"GET",headers:body?{"Content-Type":"application/json"}:{},body:body?JSON.stringify(body):undefined,credentials:"same-origin"});
 var d={};try{d=await r.json()}catch(e){}
 if(!r.ok){if(r.status===401&&path!=="login"&&path!=="me"){authed=false;user=null;showLogin()}var err=new Error(d.error||"Something went wrong. Please try again.");err.status=r.status;throw err}
 return d}
async function loadAll(){var r=await Promise.all([api("patients"),api("doctors"),api("appointments")]);db.patients=r[0];db.doctors=r[1];db.appointments=r[2];if(user&&user.role==="Admin")users=await api("users")}
async function enter(greet){authed=true;applyUser();await loadAll();var L=document.getElementById("login");L.hidden=true;L.innerHTML="";cur="dashboard";render();if(greet)toast("Welcome, "+user.name.split(" ")[0])}
var view=document.getElementById("view"),cur="dashboard",q="";
function esc(s){return String(s).replace(/[&<>"']/g,function(c){return{"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]})}
function byId(list,id){return list.find(function(x){return x.id==id})}
function toast(m,err){var t=document.getElementById("toast");t.textContent=m;t.className="show"+(err?" err":"");clearTimeout(toast.h);toast.h=setTimeout(function(){t.className=""},2400)}
function head(t,s){return"<h1>"+t+"</h1><p class='sub'>"+s+"</p>"}
function empty(m){return"<div class='empty'>"+m+"</div>"}
var IC={p:"<circle cx='12' cy='8' r='4'/><path d='M4 21c0-4 3.6-7 8-7s8 3 8 7'/>",d:"<path d='M6 3v6a4 4 0 0 0 8 0V3M10 13v3a4 4 0 0 0 8 0v-1'/><circle cx='18' cy='13' r='2'/>",a:"<rect x='3' y='5' width='18' height='16' rx='2'/><path d='M3 10h18M8 3v4M16 3v4'/>"};
function ico(k){return"<i class='ic "+k+"'><svg viewBox='0 0 24 24'>"+IC[k]+"</svg></i>"}
function hero(){var t=new Date().toLocaleDateString("en-IN",{weekday:"long",day:"numeric",month:"long"});return"<section class='hero'><div><p class='hd'>"+t+"</p><h1>Good day, "+esc(user?user.name.split(" ")[0]:"team")+"</h1><p>Here’s what’s happening across the hospital right now.</p><div class='hb'><button data-go='patients'>Add patient</button><button class='ghost' data-go='appointments'>Book appointment</button></div></div><svg class='ecg' viewBox='0 0 400 120' preserveAspectRatio='none' aria-hidden='true'><path d='M0 70 H90 l12 -8 l12 8 H150 l10 -52 l18 96 l14 -64 l8 20 H260 l12 -10 l12 10 H400' pathLength='1'/></svg></section>"}
function av(n){var c=n.replace(/^Dr\.?\s*/i,"").split(" "),i=(c[0][0]+(c[1]?c[1][0]:"")).toUpperCase(),h=0;for(var k=0;k<n.length;k++)h+=n.charCodeAt(k);return"<span class='who'><i class='av' style='--h:"+(h*37%360)+"'>"+esc(i)+"</i>"+esc(n)+"</span>"}
function chip(s){var d=new Date(s+"T00:00");if(isNaN(d))return"<span class='pill'>"+esc(s)+"</span>";return"<span class='cal'><b>"+d.getDate()+"</b>"+d.toLocaleDateString("en",{month:"short"})+"</span>"}
function table(cols,rows,msg){if(!rows.length)return empty(msg);return"<div class='scroll'><table><thead><tr>"+cols.map(function(c){return"<th>"+c+"</th>"}).join("")+"</tr></thead><tbody>"+rows.join("")+"</tbody></table></div>"}
var V={
dashboard:function(){
 var up=db.appointments.slice().sort(function(a,b){return a.date<b.date?-1:1}).slice(0,5).map(function(a){var p=byId(db.patients,a.patientId),d=byId(db.doctors,a.doctorId);return"<div><span><b>"+esc(p.name)+"</b> with "+esc(d.name)+"<br><small>"+esc(d.specialty)+"</small></span>"+chip(a.date)+"</div>"}).join("");
 return hero()+
 "<div class='stats'><div class='stat'>"+ico('p')+"<div><b>"+db.patients.length+"</b><span>Patients registered</span></div></div><div class='stat'>"+ico('d')+"<div><b>"+db.doctors.length+"</b><span>Doctors on staff</span></div></div><div class='stat'>"+ico('a')+"<div><b>"+db.appointments.length+"</b><span>Appointments booked</span></div></div></div>"+
 "<div class='card'><h2>Next appointments</h2>"+(up?"<div class='up'>"+up+"</div>":empty("No appointments yet. Book the first one from the Appointments page."))+"</div>"
},
patients:function(){
 var rows=db.patients.filter(function(p){return p.name.toLowerCase().indexOf(q)>-1}).map(function(p){return"<tr><td class='id'>#"+p.id+"</td><td>"+av(p.name)+"</td><td>"+p.age+"</td><td>"+esc(p.gender)+"</td></tr>"});
 return head("Patients","Register new patients and look up existing ones.")+
 "<div class='card'><h2>Add patient</h2><form id='f'><label>Full name<input name='name' required maxlength='60' placeholder='e.g. Rohan Mehta'></label><label>Age<input name='age' type='number' min='0' max='120' required></label><label>Gender<select name='gender'><option>Male</option><option>Female</option><option>Other</option></select></label><button>Add patient</button></form></div>"+
 "<div class='card'><div class='tools'><h2>All patients</h2><input id='s' type='search' placeholder='Search by name' value='"+esc(q)+"' aria-label='Search patients'></div>"+table(["ID","Name","Age","Gender"],rows,q?"No patient matches “"+esc(q)+"”.":"No patients yet. Add one using the form above.")+"</div>"
},
doctors:function(){
 var rows=db.doctors.map(function(d){return"<tr><td class='id'>#"+d.id+"</td><td>"+av(d.name)+"</td><td><span class='pill'>"+esc(d.specialty)+"</span></td></tr>"});
 return head("Doctors","Manage the medical staff and their specialties.")+
 "<div class='card'><h2>Add doctor</h2><form id='f'><label>Full name<input name='name' required maxlength='60' placeholder='e.g. Dr. Neha Verma'></label><label>Specialty<input name='specialty' required maxlength='40' placeholder='e.g. Neurology'></label><button>Add doctor</button></form></div>"+
 "<div class='card'><h2>All doctors</h2>"+table(["ID","Name","Specialty"],rows,"No doctors yet. Add one using the form above.")+"</div>"
},
appointments:function(){
 var ok=db.patients.length&&db.doctors.length;
 var po=db.patients.map(function(p){return"<option value='"+p.id+"'>#"+p.id+" "+esc(p.name)+"</option>"}).join(""),dop=db.doctors.map(function(d){return"<option value='"+d.id+"'>#"+d.id+" "+esc(d.name)+" ("+esc(d.specialty)+")</option>"}).join("");
 var rows=db.appointments.map(function(a){var p=byId(db.patients,a.patientId),d=byId(db.doctors,a.doctorId);return"<tr><td>"+av(p.name)+"</td><td>"+av(d.name)+"</td><td>"+chip(a.date)+"</td></tr>"});
 return head("Appointments","Match a patient with a doctor and pick a date.")+
 "<div class='card'><h2>Book appointment</h2>"+(ok?"<form id='f'><label>Patient<select name='patientId'>"+po+"</select></label><label>Doctor<select name='doctorId'>"+dop+"</select></label><label>Date<input name='date' type='date' required></label><button>Book appointment</button></form>":empty("Add at least one patient and one doctor before booking."))+"</div>"+
 "<div class='card'><h2>All appointments</h2>"+table(["Patient","Doctor","Date"],rows,"No appointments booked yet.")+"</div>"
}};
var MODES={ask:{t:"Ask",h:"Ask anything about your patients, doctors or appointments.",ph:"e.g. Which doctor has the most appointments?"},
sym:{t:"Symptoms",h:"Describe the symptoms. You get a suggested specialist and urgency, not a diagnosis.",ph:"e.g. 3 din se seene mein dard aur saans phoolna"},
book:{t:"Book by text",h:"Write the booking in plain words and confirm it.",ph:"e.g. Book Meera with Dr. Khan on 2026-10-28"},
note:{t:"Notes summary",h:"Paste a doctor’s rough notes to get a clean visit summary.",ph:"e.g. pt c/o fever 3d, cough, temp 101, advised rest + paracetamol, review 5d"},
ins:{t:"Insights",h:"A short, plain-language read of the hospital data.",ph:""}};
var aiMode="ask";
V.ai=function(){var m=MODES[aiMode];return head("CareDesk AI","Ask questions, check symptoms, book appointments and summarize notes in plain words. Replies in English, Hindi or Hinglish.")+
"<div class='chips'>"+Object.keys(MODES).map(function(k){return"<button class='chip"+(k===aiMode?" on":"")+"' data-m='"+k+"'>"+MODES[k].t+"</button>"}).join("")+"</div>"+
"<div class='card'><h2>"+m.t+"</h2><p class='sub' style='margin-bottom:14px'>"+m.h+"</p>"+(aiMode==="ins"?"":"<textarea id='ai-in' rows='3' placeholder='"+esc(m.ph)+"'></textarea>")+
"<div class='tools' style='margin:14px 0 0'><button id='ai-go'>"+({ask:"Get answer",sym:"Check symptoms",book:"Prepare booking",note:"Summarize notes",ins:"Generate insights"}[aiMode])+"</button><small class='mut'>AI-generated. Always confirm medical decisions with a doctor.</small></div></div><div class='card' id='ai-out' hidden></div>"};
function fmt(t){return esc(t).replace(/\*\*(.+?)\*\*/g,"<b>$1</b>").replace(/\n/g,"<br>")}
function bindAI(){document.querySelectorAll(".chip").forEach(function(b){b.onclick=function(){aiMode=b.dataset.m;render()}});document.getElementById("ai-go").onclick=runAI}
async function runAI(){
 var out=document.getElementById("ai-out"),inp=document.getElementById("ai-in"),txt=inp?inp.value.trim():"",btn=document.getElementById("ai-go");
 if(aiMode!=="ins"&&!txt){toast("Write something first",1);return}
 out.hidden=false;out.innerHTML="<p class='mut'>CareDesk AI is thinking…</p>";btn.disabled=true;
 try{
  var r=await api("ai","POST",{mode:aiMode,text:txt});
  if(aiMode==="book"){
   var p=byId(db.patients,r.patientId),d=byId(db.doctors,r.doctorId);
   if(p&&d&&/^\d{4}-\d\d-\d\d$/.test(r.date||"")){
    out.innerHTML="<p>"+esc(r.reply||"Ready to book.")+"</p><div class='conf'>"+av(p.name)+av(d.name)+chip(r.date)+"</div><button id='ai-ok'>Confirm booking</button>";
    document.getElementById("ai-ok").onclick=function(){api("appointments","POST",{patientId:p.id,doctorId:d.id,date:r.date}).then(loadAll).then(function(){toast("Appointment booked");cur="appointments";render()}).catch(function(e){toast(e.message,1)})};
   }else out.innerHTML="<p>"+esc(r.reply||"I couldn’t match a patient, doctor and date. Try including all three.")+"</p>";
  }else out.innerHTML="<div class='ans'>"+fmt(r.text)+"</div>";
 }catch(e){out.innerHTML="<p class='mut'>"+esc(e.message||"Couldn’t get an answer. Try again in a moment.")+"</p>"}
 btn.disabled=false;
}
var A={
patients:async function(f){await api("patients","POST",{name:f.name.trim(),age:+f.age,gender:f.gender});toast("Patient added")},
doctors:async function(f){await api("doctors","POST",{name:f.name.trim(),specialty:f.specialty.trim()});toast("Doctor added")},
appointments:async function(f){await api("appointments","POST",{patientId:+f.patientId,doctorId:+f.doctorId,date:f.date});toast("Appointment booked")}};
function render(){
 view.innerHTML=V[cur]();
 document.querySelectorAll("#nav a").forEach(function(a){a.classList.toggle("on",a.dataset.v===cur)});
 var f=document.getElementById("f");
 if(f)f.onsubmit=function(e){e.preventDefault();var o={};new FormData(f).forEach(function(v,k){o[k]=v});if(!Object.values(o).every(function(v){return String(v).trim()})){toast("Please fill every field",1);return}A[cur](o).then(loadAll).then(render).catch(function(e){toast(e.message,1)})};
 document.querySelectorAll("[data-go]").forEach(function(b){b.onclick=function(){document.querySelector("#nav a[data-v='"+b.dataset.go+"']").click()}});
 document.querySelectorAll("[data-rm]").forEach(function(b){b.onclick=function(){var id=+b.dataset.rm;api("users/"+id,"DELETE").then(function(){toast("Account removed");return loadAll()}).then(render).catch(function(e){toast(e.message,1)})}});
 if(cur==="ai")bindAI();
 var s=document.getElementById("s");
 if(s)s.oninput=function(){q=s.value.toLowerCase();var p=s.selectionStart;render();var n=document.getElementById("s");n.focus();n.setSelectionRange(p,p)};
}
document.querySelectorAll("#nav a").forEach(function(a){
 function go(){cur=a.dataset.v;q="";render();scrollTo(0,0)}
 a.onclick=go;a.onkeydown=function(e){if(e.key==="Enter"||e.key===" "){e.preventDefault();go()}};
});
var users=[];
var user=null;
function applyUser(){document.getElementById("nav-staff").hidden=!(user&&user.role==="Admin");var m=document.getElementById("me");m.innerHTML=user?"<i class='av' style='--h:"+(user.name.length*47%360)+"'>"+esc(user.name.slice(0,2).toUpperCase())+"</i><div>"+esc(user.name)+"<small>"+esc(user.role)+"</small></div>":""}
V.staff=function(){
 var rows=users.map(function(u){return"<tr><td>"+av(u.name)+"</td><td>@"+esc(u.username)+"</td><td><span class='pill'>"+esc(u.role)+"</span></td><td>"+(u.role==="Admin"?"":"<button class='rm' data-rm='"+u.id+"'>Remove</button>")+"</td></tr>"});
 return head("Staff","Create accounts for your team. Only admins can see this page.")+
 "<div class='card'><h2>Add staff member</h2><form id='f'><label>Full name<input name='name' required maxlength='60'></label><label>Username<input name='username' required maxlength='24' autocomplete='off' placeholder='letters and numbers'></label><label>Temporary password<input name='password' type='password' required minlength='6' autocomplete='new-password'></label><label>Role<select name='role'><option>Receptionist</option><option>Doctor</option><option>Nurse</option></select></label><button>Create account</button></form></div>"+
 "<div class='card'><h2>All accounts</h2>"+table(["Name","Username","Role",""],rows,"No accounts yet.")+"</div>"};
A.staff=async function(f){var un=f.username.trim().toLowerCase();if(!/^[a-z0-9_.]{3,24}$/.test(un))throw new Error("Username needs 3 to 24 letters or numbers");await api("users","POST",{name:f.name.trim(),username:un,password:f.password,role:f.role});toast("Account created for "+f.name.trim())};
var authed=false;
function showLogin(){
 var L=document.getElementById("login");L.hidden=false;
 L.innerHTML="<section class='lg-art'><div class='brand'><i><svg viewBox='0 0 24 24'><path d='M12 5v14M5 12h14'/></svg></i><span>CareDesk</span></div><h1>Care that runs smoothly.</h1><p>Patients, doctors and appointments in one place, with an AI assistant to help your team.</p><svg class='ecg' viewBox='0 0 400 120' preserveAspectRatio='none' aria-hidden='true'><path d='M0 70 H90 l12 -8 l12 8 H150 l10 -52 l18 96 l14 -64 l8 20 H260 l12 -10 l12 10 H400' pathLength='1'/></svg></section><section class='lg-form'><form id='lf'><h2>Staff sign in</h2><p class='sub'>Use your admin account to continue.</p><label>Username<input id='lu' autocomplete='username' required></label><label>Password<input id='lp' type='password' autocomplete='current-password' required></label><p id='le' class='lerr' role='alert' hidden></p><button>Sign in</button><small class='mut'>Need an account? Ask your administrator.</small></form></section>";
 document.getElementById("lu").focus();
 document.getElementById("lf").onsubmit=async function(e){e.preventDefault();
  var u=document.getElementById("lu").value.trim(),p=document.getElementById("lp").value,er=document.getElementById("le"),b=L.querySelector("button");
  b.disabled=true;er.hidden=true;
  try{user=await api("login","POST",{username:u,password:p});await enter(true)}
  catch(x){er.hidden=false;er.textContent=x.message;document.getElementById("lp").value="";document.getElementById("lp").focus()}
  b.disabled=false};
}
document.getElementById("out").onclick=function(){api("logout","POST").catch(function(){}).then(function(){authed=false;user=null;users=[];db={patients:[],doctors:[],appointments:[]};applyUser();showLogin()})};
(async function(){try{user=await api("me");await enter(false)}catch(e){render();showLogin()}})();
