const loginTab = document.getElementById('loginTab');
const signupTab = document.getElementById('signupTab');
const title = document.getElementById('formTitle');
const subtitle = document.getElementById('formSubtitle');
const submit = document.getElementById('submitBtn');
const forgot = document.getElementById('forgotRow');
const password = document.getElementById('password');
const message = document.getElementById('formMessage');

let mode = 'login';

function setMode(next) {
  mode = next;
  const signup = mode === 'signup';
  loginTab.classList.toggle('active', !signup);
  signupTab.classList.toggle('active', signup);
  title.textContent = signup ? 'Create Account' : 'Welcome Back!';
  subtitle.textContent = signup
    ? 'Sign up to discover your next campus event.'
    : 'Login to your account to continue.';
  submit.textContent = signup ? 'Create Account' : 'Login';
  forgot.hidden = signup;
  password.autocomplete = signup ? 'new-password' : 'current-password';
  message.textContent = '';
}

loginTab.addEventListener('click', () => setMode('login'));
signupTab.addEventListener('click', () => setMode('signup'));

if (new URLSearchParams(location.search).get('mode') === 'signup') {
  setMode('signup');
}

document.getElementById('authForm').addEventListener('submit', (e) => {
  e.preventDefault();
  message.textContent =
    mode === 'signup'
      ? 'Demo only: account creation needs backend authentication.'
      : 'Demo only: login needs backend authentication.';
});

document.getElementById('forgotLink').addEventListener('click', (e) => {
  e.preventDefault();
  message.textContent =
    'Password reset will be available after authentication is connected.';
});

document.getElementById('googleBtn').addEventListener('click', () => {
  message.textContent = 'Google sign-in is a UI demo and is not connected yet.';
});

document.getElementById('appleBtn').addEventListener('click', () => {
  message.textContent = 'Apple sign-in is a UI demo and is not connected yet.';
});