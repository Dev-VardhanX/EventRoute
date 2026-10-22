// EventRoute starter: all event content below is fictional demo data.
const demoEvents = [
  {id:1,category:"Hackathon",title:"Tryst: Robotics Cup",college:"IIT Delhi · Hauz Khas",date:"Sat, 24 Oct",fee:"FREE",travel:"38 min away",art:"art-hack",emoji:"🤖",description:"Build, test and compete in a friendly robotics challenge."},
  {id:2,category:"Cultural",title:"Engifest: Open Mic Night",college:"DTU · Rohini",date:"Sun, 25 Oct",fee:"₹200",travel:"52 min away",art:"art-culture",emoji:"🎤",description:"A feel-good evening of music, poetry and campus talent."},
  {id:3,category:"Workshop",title:"Moksha: AI Innovation",college:"NSUT · Dwarka",date:"Sat, 24 Oct",fee:"FREE",travel:"24 min away",art:"art-workshop",emoji:"🧠",description:"Explore practical AI ideas with student builders and mentors."},
  {id:4,category:"Hackathon",title:"CodeSprint 2026",college:"IGDTUW · Kashmere Gate",date:"Sun, 25 Oct",fee:"FREE",travel:"44 min away",art:"art-sport",emoji:"💻",description:"A fast-paced coding challenge for curious problem solvers."},
  {id:5,category:"Workshop",title:"Design Jam: Make It Pop",college:"Amity University · Noida",date:"Sat, 24 Oct",fee:"₹150",travel:"48 min away",art:"art-design",emoji:"🎨",description:"Turn rough ideas into polished product concepts in a day."},
  {id:6,category:"Cultural",title:"Campus Beats Festival",college:"Shiv Nadar University · Greater Noida",date:"Sun, 25 Oct",fee:"₹250",travel:"65 min away",art:"art-music",emoji:"🎶",description:"Live performances, student bands and a festival atmosphere."}
];

const eventGrid = document.getElementById("eventGrid");
const eventSearch = document.getElementById("eventSearch");
const filterButtons = [...document.querySelectorAll("[data-filter]")];
let activeFilter = "All";

function renderEvents() {
  const query = eventSearch.value.trim().toLowerCase();
  const filtered = demoEvents.filter(event => {
    const matchesCategory = activeFilter === "All" || event.category === activeFilter;
    const searchable = `${event.title} ${event.college} ${event.category}`.toLowerCase();
    return matchesCategory && searchable.includes(query);
  });

  if (!filtered.length) {
    eventGrid.innerHTML = '<div class="empty-state"><strong>No events found</strong><br>Try another search or category.</div>';
    return;
  }

  eventGrid.innerHTML = filtered.map(event => `
    <article class="event-card">
      <div class="event-art ${event.art}">
        <span class="event-category">${event.category.toUpperCase()}</span>
        <span class="event-emoji" aria-hidden="true">${event.emoji}</span>
        <span class="travel-chip">◷ ${event.travel}</span>
      </div>
      <div class="event-content">
        <h3>${escapeHTML(event.title)}</h3>
        <p class="event-college">⌖ ${escapeHTML(event.college)}</p>
        <div class="event-meta">
          <span class="event-date">${escapeHTML(event.date)}</span>
          <span class="event-price">${escapeHTML(event.fee)}</span>
          <button class="event-details" data-event="${event.id}">Details ↗</button>
        </div>
      </div>
    </article>
  `).join("");
}

function escapeHTML(value) {
  return String(value).replace(/[&<>"']/g, char => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  })[char]);
}

filterButtons.forEach(button => {
  button.addEventListener("click", () => {
    activeFilter = button.dataset.filter;
    filterButtons.forEach(item => item.classList.toggle("active", item === button));
    renderEvents();
  });
});
eventSearch.addEventListener("input", renderEvents);

const toast = document.getElementById("toast");
let toastTimer;
function showToast(message) {
  toast.textContent = message;
  toast.classList.add("show");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove("show"), 2800);
}

document.addEventListener("click", event => {
  const toastButton = event.target.closest("[data-toast]");
  if (toastButton) showToast(toastButton.dataset.toast);
  const detailsButton = event.target.closest("[data-event]");
  if (detailsButton) {
    const item = demoEvents.find(item => item.id === Number(detailsButton.dataset.event));
    if (item) showToast(`${item.title}: ${item.description} (Demo preview)`);
  }
});

document.getElementById("menuToggle").addEventListener("click", () => {
  document.querySelector(".desktop-nav").classList.toggle("open");
});

renderEvents();


// Gentle reveal-on-scroll animations (with graceful fallback).
const revealTargets = document.querySelectorAll(
  ".section-heading, .step-card, .event-card, .team-banner, .app-panel, .footer-main"
);
revealTargets.forEach((el, index) => {
  el.classList.add("reveal");
  el.style.transitionDelay = `${(index % 3) * 80}ms`;
});
if ("IntersectionObserver" in window) {
  const revealObserver = new IntersectionObserver((entries, observer) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        entry.target.classList.add("is-visible");
        observer.unobserve(entry.target);
      }
    });
  }, { threshold: 0.12 });
  revealTargets.forEach(el => revealObserver.observe(el));
} else {
  revealTargets.forEach(el => el.classList.add("is-visible"));
}
