import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ErrorDeApi, solicitarUrlDeSubida, subirDocumento } from "@/lib/api";

import { CapturaDeDocumento } from "./CapturaDeDocumento";

vi.mock("@/lib/api", async (importOriginal) => {
  const real = await importOriginal<typeof import("@/lib/api")>();
  return { ...real, solicitarUrlDeSubida: vi.fn(), subirDocumento: vi.fn() };
});

/** Falso `MediaStream`: lo único que el componente le pide es poder detener sus pistas. */
function streamFalso() {
  return { getTracks: () => [{ stop: vi.fn() }] } as unknown as MediaStream;
}

/** Un `File` de prueba, del tamaño y tipo que pida cada caso. */
function archivoDePrueba({
  nombre = "dni.jpg",
  tipo = "image/jpeg",
  bytes = 1024,
}: { nombre?: string; tipo?: string; bytes?: number } = {}) {
  return new File([new Uint8Array(bytes)], nombre, { type: tipo });
}

/**
 * AYNI-13 subtareas 12 y 13: cámara con guía visual de encuadre, con carga desde archivo
 * como alternativa siempre disponible.
 *
 * jsdom no implementa `getUserMedia`, `canvas.toBlob` ni `URL.createObjectURL` — se
 * sustituyen a mano. Lo que se prueba es la máquina de estados (permiso → en vivo → foto
 * tomada → subida), no que la cámara real funcione: eso solo se ve en un navegador de
 * verdad, y por eso mismo no se automatiza aquí.
 */
describe("CapturaDeDocumento · AYNI-13 subtareas 12 y 13", () => {
  const getUserMedia = vi.fn();
  const onCompletado = vi.fn();

  // jsdom no implementa `getContext`/`toBlob` de forma útil (sin el paquete `canvas`), así
  // que se sustituyen a mano y se restauran aparte: `vi.restoreAllMocks()` solo deshace
  // espías creados con `vi.spyOn`, no una asignación directa como esta.
  const getContextOriginal = HTMLCanvasElement.prototype.getContext;
  const toBlobOriginal = HTMLCanvasElement.prototype.toBlob;

  beforeEach(() => {
    Object.defineProperty(navigator, "mediaDevices", {
      value: { getUserMedia },
      configurable: true,
    });

    HTMLCanvasElement.prototype.getContext = vi.fn().mockReturnValue({ drawImage: vi.fn() });
    HTMLCanvasElement.prototype.toBlob = vi.fn((callback: BlobCallback) => {
      callback(new Blob(["foto"], { type: "image/jpeg" }));
    });

    vi.stubGlobal("URL", {
      ...URL,
      createObjectURL: vi.fn(() => "blob:vista-previa"),
      revokeObjectURL: vi.fn(),
    });
  });

  afterEach(() => {
    // Desmontar aquí, antes de deshacer los stubs: el `afterEach` de este fichero se
    // ejecuta antes que el `cleanup()` global de `vitest.setup.ts` (los hooks de un
    // fichero de pruebas corren antes que los de sus setupFiles). Sin este desmontaje
    // explícito, el desmontaje real llegaría después de restaurar el `URL` nativo de
    // jsdom, que no implementa `revokeObjectURL`.
    cleanup();
    HTMLCanvasElement.prototype.getContext = getContextOriginal;
    HTMLCanvasElement.prototype.toBlob = toBlobOriginal;
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  async function renderizarConCamaraLista() {
    getUserMedia.mockResolvedValue(streamFalso());
    render(
      <CapturaDeDocumento
        solicitudId="11111111-1111-1111-1111-111111111111"
        tipoDocumento="ANVERSO"
        cara="Anverso"
        onCompletado={onCompletado}
      />,
    );
    await screen.findByRole("button", { name: /tomar foto/i });
  }

  it("pide la cámara y muestra el marco guía cuando el permiso se concede", async () => {
    await renderizarConCamaraLista();

    expect(getUserMedia).toHaveBeenCalledWith({
      video: { facingMode: "environment" },
      audio: false,
    });
    expect(screen.getByText(/encuadra tu dni dentro del marco/i)).toBeInTheDocument();
  });

  it("si el permiso se deniega, avisa en vez de dejar la pantalla en blanco", async () => {
    getUserMedia.mockRejectedValue(new DOMException("Denegado", "NotAllowedError"));
    render(
      <CapturaDeDocumento
        solicitudId="11111111-1111-1111-1111-111111111111"
        tipoDocumento="ANVERSO"
        cara="Anverso"
        onCompletado={onCompletado}
      />,
    );

    expect(await screen.findByText(/no pudimos acceder a tu cámara/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /tomar foto/i })).not.toBeInTheDocument();
  });

  it("tomar la foto pasa a la vista de revisión", async () => {
    const usuario = userEvent.setup();
    await renderizarConCamaraLista();

    await usuario.click(screen.getByRole("button", { name: /tomar foto/i }));

    expect(await screen.findByAltText(/foto del dni/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /volver a tomar/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /usar esta foto/i })).toBeInTheDocument();
  });

  it("volver a tomar regresa a la cámara sin pedir permiso otra vez", async () => {
    const usuario = userEvent.setup();
    await renderizarConCamaraLista();

    await usuario.click(screen.getByRole("button", { name: /tomar foto/i }));
    await usuario.click(await screen.findByRole("button", { name: /volver a tomar/i }));

    expect(await screen.findByRole("button", { name: /tomar foto/i })).toBeInTheDocument();
    // Un único permiso pedido para toda la sesión de captura: reintentar no debe volver a
    // disparar el diálogo del navegador.
    expect(getUserMedia).toHaveBeenCalledTimes(1);
  });

  it("usar esta foto pide la URL de subida, sube el archivo y avisa que terminó", async () => {
    const usuario = userEvent.setup();
    vi.mocked(solicitarUrlDeSubida).mockResolvedValue({
      url: "https://minio.local/presigned",
      expiraEn: "2026-09-12T10:05:00Z",
    });
    vi.mocked(subirDocumento).mockResolvedValue(undefined);

    await renderizarConCamaraLista();
    await usuario.click(screen.getByRole("button", { name: /tomar foto/i }));
    await usuario.click(await screen.findByRole("button", { name: /usar esta foto/i }));

    await waitFor(() => expect(onCompletado).toHaveBeenCalledTimes(1));

    expect(solicitarUrlDeSubida).toHaveBeenCalledWith(
      "11111111-1111-1111-1111-111111111111",
      "ANVERSO",
      "jpg",
    );
    const [urlUsada, archivoUsado] = vi.mocked(subirDocumento).mock.calls[0];
    expect(urlUsada).toBe("https://minio.local/presigned");
    expect(archivoUsado).toBeInstanceOf(File);
    expect(archivoUsado.type).toBe("image/jpeg");
  });

  it("si la subida falla, avisa y deja reintentar sin perder la foto tomada", async () => {
    const usuario = userEvent.setup();
    vi.mocked(solicitarUrlDeSubida).mockResolvedValue({
      url: "https://minio.local/presigned",
      expiraEn: "2026-09-12T10:05:00Z",
    });
    vi.mocked(subirDocumento).mockRejectedValue(
      new ErrorDeApi({ title: "No pudimos subir el documento", status: 503 }, 503),
    );

    await renderizarConCamaraLista();
    await usuario.click(screen.getByRole("button", { name: /tomar foto/i }));
    await usuario.click(await screen.findByRole("button", { name: /usar esta foto/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/no pudimos subir el documento/i);
    // Sigue en revisión: la foto no se pierde y se puede reintentar sin volver a fotografiar.
    expect(screen.getByRole("button", { name: /usar esta foto/i })).toBeInTheDocument();
    expect(onCompletado).not.toHaveBeenCalled();
  });

  describe("carga desde archivo (subtarea 13)", () => {
    it("el enlace para subir un archivo está disponible aunque la cámara funcione", async () => {
      await renderizarConCamaraLista();

      expect(
        screen.getByRole("button", { name: /subir un archivo en su lugar/i }),
      ).toBeInTheDocument();
    });

    it("cambiar a archivo detiene la cámara y muestra la zona de carga", async () => {
      const usuario = userEvent.setup();
      await renderizarConCamaraLista();

      await usuario.click(screen.getByRole("button", { name: /subir un archivo en su lugar/i }));

      expect(screen.getByLabelText(/arrastra tu archivo/i)).toBeInTheDocument();
      expect(screen.queryByRole("button", { name: /tomar foto/i })).not.toBeInTheDocument();
    });

    it("un archivo también sirve para quien no tiene cámara", async () => {
      const usuario = userEvent.setup();
      getUserMedia.mockRejectedValue(new DOMException("Sin dispositivo", "NotFoundError"));
      render(
        <CapturaDeDocumento
          solicitudId="11111111-1111-1111-1111-111111111111"
          tipoDocumento="ANVERSO"
          cara="Anverso"
          onCompletado={onCompletado}
        />,
      );
      await screen.findByText(/no pudimos acceder a tu cámara/i);

      await usuario.click(screen.getByRole("button", { name: /subir un archivo en su lugar/i }));

      expect(screen.getByLabelText(/arrastra tu archivo/i)).toBeInTheDocument();
    });

    it("un archivo JPG válido pasa a la vista de revisión", async () => {
      const usuario = userEvent.setup();
      await renderizarConCamaraLista();
      await usuario.click(screen.getByRole("button", { name: /subir un archivo en su lugar/i }));

      await usuario.upload(screen.getByLabelText(/arrastra tu archivo/i), archivoDePrueba());

      expect(await screen.findByAltText(/foto del dni/i)).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /elegir otro archivo/i })).toBeInTheDocument();
    });

    it("rechaza un tipo de archivo que el backend no acepta", async () => {
      const usuario = userEvent.setup();
      await renderizarConCamaraLista();
      await usuario.click(screen.getByRole("button", { name: /subir un archivo en su lugar/i }));

      // `fireEvent` y no `userEvent.upload`: éste último respeta el `accept` del input y
      // ni siquiera aplicaría un PDF, pero soltar un archivo arrastrado no pasa por esa
      // validación del navegador — y es justo el camino que la validación propia cubre.
      fireEvent.change(screen.getByLabelText(/arrastra tu archivo/i), {
        target: { files: [archivoDePrueba({ nombre: "dni.pdf", tipo: "application/pdf" })] },
      });

      expect(await screen.findByRole("alert")).toHaveTextContent(/jpg o png/i);
      expect(screen.queryByAltText(/foto del dni/i)).not.toBeInTheDocument();
    });

    it("rechaza un archivo que pesa más de 10 MB", async () => {
      const usuario = userEvent.setup();
      await renderizarConCamaraLista();
      await usuario.click(screen.getByRole("button", { name: /subir un archivo en su lugar/i }));

      await usuario.upload(
        screen.getByLabelText(/arrastra tu archivo/i),
        archivoDePrueba({ bytes: 11 * 1024 * 1024 }),
      );

      expect(await screen.findByRole("alert")).toHaveTextContent(/10 mb/i);
    });

    it("sube el archivo elegido con su propia extensión", async () => {
      const usuario = userEvent.setup();
      vi.mocked(solicitarUrlDeSubida).mockResolvedValue({
        url: "https://minio.local/presigned",
        expiraEn: "2026-09-12T10:05:00Z",
      });
      vi.mocked(subirDocumento).mockResolvedValue(undefined);

      await renderizarConCamaraLista();
      await usuario.click(screen.getByRole("button", { name: /subir un archivo en su lugar/i }));
      await usuario.upload(
        screen.getByLabelText(/arrastra tu archivo/i),
        archivoDePrueba({ nombre: "escaneo.png", tipo: "image/png" }),
      );
      await usuario.click(await screen.findByRole("button", { name: /usar esta foto/i }));

      await waitFor(() => expect(onCompletado).toHaveBeenCalledTimes(1));
      expect(solicitarUrlDeSubida).toHaveBeenCalledWith(
        "11111111-1111-1111-1111-111111111111",
        "ANVERSO",
        "png",
      );
    });

    it("volver a la cámara desde archivo pide el permiso de nuevo", async () => {
      const usuario = userEvent.setup();
      await renderizarConCamaraLista();
      await usuario.click(screen.getByRole("button", { name: /subir un archivo en su lugar/i }));

      await usuario.click(screen.getByRole("button", { name: /usar la cámara en su lugar/i }));

      await screen.findByRole("button", { name: /tomar foto/i });
      expect(getUserMedia).toHaveBeenCalledTimes(2);
    });
  });
});
