import { ShieldAlert } from "lucide-react";
import { useState, useRef, useEffect, UIEvent } from "react";
import { Boton } from "@/componentes/Boton";

interface Props {
  onConsentimientoOtorgado: () => void;
  onRechazado: () => void;
}

export function ConsentimientoBiometrico({ onConsentimientoOtorgado, onRechazado }: Props) {
  const [terminosAceptados, setTerminosAceptados] = useState(false);
  const [politicaAceptada, setPoliticaAceptada] = useState(false);
  const [leidoHastaElFinal, setLeidoHastaElFinal] = useState(false);
  
  const scrollRef = useRef<HTMLDivElement>(null);

  const revisarScroll = (e: UIEvent<HTMLDivElement>) => {
    const target = e.currentTarget;
    // Tolerancia de 5px
    if (target.scrollHeight - target.scrollTop <= target.clientHeight + 5) {
      setLeidoHastaElFinal(true);
    }
  };

  // Si el texto es corto y no requiere scroll, marcar como leído
  useEffect(() => {
    if (scrollRef.current) {
      if (scrollRef.current.scrollHeight <= scrollRef.current.clientHeight) {
        setLeidoHastaElFinal(true);
      }
    }
  }, []);

  const todoAceptado = terminosAceptados && politicaAceptada && leidoHastaElFinal;

  return (
    <div className="flex flex-col gap-6 max-w-2xl mx-auto p-6 bg-blanco rounded-[18px] shadow-sm border border-gris-300">
      <div className="flex items-center gap-3">
        <ShieldAlert className="h-8 w-8 text-azul-700" />
        <h1 className="text-2xl font-bold text-azul-900">Consentimiento Biométrico</h1>
      </div>

      <div className="bg-azul-050 p-4 rounded-lg text-sm text-azul-800">
        <p>De conformidad con la Ley N° 29733, Ley de Protección de Datos Personales, necesitamos tu autorización expresa para capturar y procesar tus datos biométricos (fotografía facial).</p>
      </div>

      <div 
        ref={scrollRef}
        onScroll={revisarScroll}
        className="border border-gris-300 rounded-lg p-4 h-64 overflow-y-auto text-sm text-gris-700 space-y-4"
        tabIndex={0}
      >
        <h3 className="font-bold">1. Finalidad del Tratamiento</h3>
        <p>Tus datos biométricos serán utilizados única y exclusivamente con la finalidad de verificar tu identidad durante el proceso de apertura de cuenta (Onboarding Digital), comparando la fotografía (selfie) tomada en tiempo real con la imagen de tu Documento Nacional de Identidad (DNI).</p>
        
        <h3 className="font-bold">2. Banco de Datos</h3>
        <p>Tus datos serán almacenados en el banco de datos &quot;Clientes&quot; de titularidad de Ayni Bank.</p>
        
        <h3 className="font-bold">3. Plazo de Conservación</h3>
        <p>Los datos serán conservados mientras dure el proceso de verificación. En caso de apertura exitosa, se mantendrán conforme a los plazos exigidos por la normativa aplicable (SBS). En caso de rechazo, serán eliminados inmediatamente.</p>
        
        <h3 className="font-bold">4. Ejercicio de Derechos ARCO</h3>
        <p>Puedes ejercer tus derechos de Acceso, Rectificación, Cancelación y Oposición enviando un correo a derechosarco@aynibank.pe.</p>
      </div>

      <div className="space-y-4">
        <label className="flex items-start gap-3 cursor-pointer">
          <input 
            type="checkbox" 
            className="mt-1 h-5 w-5 text-azul-600 rounded border-gris-300 focus:ring-azul-500 disabled:opacity-50"
            disabled={!leidoHastaElFinal}
            checked={terminosAceptados}
            onChange={(e) => setTerminosAceptados(e.target.checked)}
          />
          <span className="text-sm text-gris-800">
            He leído y acepto los Términos y Condiciones del servicio biométrico.
          </span>
        </label>
        
        <label className="flex items-start gap-3 cursor-pointer">
          <input 
            type="checkbox" 
            className="mt-1 h-5 w-5 text-azul-600 rounded border-gris-300 focus:ring-azul-500 disabled:opacity-50"
            disabled={!leidoHastaElFinal}
            checked={politicaAceptada}
            onChange={(e) => setPoliticaAceptada(e.target.checked)}
          />
          <span className="text-sm text-gris-800">
            Acepto la Política de Privacidad y el tratamiento de mis datos sensibles.
          </span>
        </label>
      </div>

      {!leidoHastaElFinal && (
        <p className="text-xs text-error font-medium text-center">
          * Por favor, lee el documento completo (haz scroll hasta el final) para habilitar las opciones.
        </p>
      )}

      <div className="flex gap-4 pt-4 border-t border-gris-200">
        <Boton variante="contorno" onClick={onRechazado} className="flex-1">
          No acepto
        </Boton>
        <Boton 
          onClick={onConsentimientoOtorgado} 
          disabled={!todoAceptado} 
          className="flex-1"
        >
          Continuar
        </Boton>
      </div>
    </div>
  );
}
