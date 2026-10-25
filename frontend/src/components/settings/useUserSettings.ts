"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import { describeError } from "@/lib/api/errors";
import type { UserSettings } from "@/lib/api/types";
import { usePrefs } from "@/store/prefs";
import { toast } from "@/components/ui/Toast";
import { translate } from "@/i18n/runtime";

/**
 * Configurações do usuário (GET/PUT /me/settings) com atualização otimista: a mudança vale na hora
 * (tema, fonte, idioma — sem recarregar) e é persistida no servidor; em caso de erro, desfaz e avisa.
 */
export function useUserSettings() {
  const [settings, setSettings] = useState<UserSettings | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState<keyof UserSettings | null>(null);
  const applyServer = usePrefs((s) => s.applyServer);
  const alive = useRef(true);

  useEffect(() => {
    alive.current = true;
    api.auth
      .settings()
      .then((s) => {
        if (!alive.current) return;
        setSettings(s);
        applyServer(s);
      })
      .catch((e) => alive.current && setError(describeError(e)));
    return () => {
      alive.current = false;
    };
  }, [applyServer]);

  const update = useCallback(
    async (patch: Partial<UserSettings>) => {
      if (!settings) return;
      const prev = settings;
      const next = { ...settings, ...patch };
      setSettings(next);
      applyServer(next);
      setSaving(Object.keys(patch)[0] as keyof UserSettings);
      try {
        const saved = await api.auth.updateSettings(patch);
        if (!alive.current) return;
        setSettings(saved);
        applyServer(saved);
        toast(translate("settings", "saved"), "good");
      } catch (e) {
        if (!alive.current) return;
        setSettings(prev);
        applyServer(prev);
        toast(describeError(e), "bad");
      } finally {
        if (alive.current) setSaving(null);
      }
    },
    [settings, applyServer],
  );

  return { settings, error, saving, update };
}
