import defaultSettings from '@/settings'

export default function getPageTitle(pageTitle) {
	const title = defaultSettings.title

	if (pageTitle) {
		return `${pageTitle} - ${title}`
	}
	return title
}
