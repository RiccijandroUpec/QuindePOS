/* Guías de Quinde POS: barra de progreso de lectura e índice que marca la sección actual. */
(function () {
  var articulo = document.querySelector('.articulo');
  var barra = document.querySelector('.progreso');
  var indice = document.querySelector('.indice details');

  if (indice && window.matchMedia('(min-width: 1024px)').matches) {
    indice.open = true;
  }

  if (barra && articulo) {
    var actualizar = function () {
      var caja = articulo.getBoundingClientRect();
      var total = caja.height - window.innerHeight;
      var avance = total > 0 ? Math.min(Math.max(-caja.top / total, 0), 1) : 1;
      barra.style.width = (avance * 100) + '%';
    };
    window.addEventListener('scroll', actualizar, { passive: true });
    window.addEventListener('resize', actualizar);
    actualizar();
  }

  var enlaces = document.querySelectorAll('.indice ol a');
  if (!enlaces.length || !('IntersectionObserver' in window)) {
    return;
  }

  var porId = {};
  enlaces.forEach(function (a) {
    porId[a.getAttribute('href').slice(1)] = a;
    a.addEventListener('click', function () {
      if (!window.matchMedia('(min-width: 1024px)').matches && indice) {
        indice.open = false;
      }
    });
  });

  var observador = new IntersectionObserver(function (entradas) {
    entradas.forEach(function (e) {
      if (e.isIntersecting && porId[e.target.id]) {
        enlaces.forEach(function (a) { a.classList.remove('activo'); });
        porId[e.target.id].classList.add('activo');
      }
    });
  }, { rootMargin: '-90px 0px -70% 0px' });

  Object.keys(porId).forEach(function (id) {
    var h = document.getElementById(id);
    if (h) {
      observador.observe(h);
    }
  });
})();
